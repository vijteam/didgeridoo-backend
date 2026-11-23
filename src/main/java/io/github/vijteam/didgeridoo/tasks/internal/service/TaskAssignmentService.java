package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskConflictException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.AssignmentSource;
import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class TaskAssignmentService {
    private final TaskRepository tasks;
    private final TaskChangeRepository changes;
    private final TaskStatusRepository statuses;
    private final ObjectProvider<TaskSpaceAccess> access;

    public TaskAssignmentService(
            TaskRepository tasks,
            TaskChangeRepository changes,
            TaskStatusRepository statuses,
            ObjectProvider<TaskSpaceAccess> access) {
        this.tasks = tasks;
        this.changes = changes;
        this.statuses = statuses;
        this.access = access;
    }

    /** Trusted caller supplies the authenticated actor; no client role is accepted. */
    public TaskView claim(UUID spaceId, UUID actorId, UUID taskId) {
        requireWriter(spaceId, actorId, taskId);
        Task task = lockedTask(spaceId, taskId);
        if (task.getAssigneeId() != null) {
            throw new TaskConflictException("Task is already assigned");
        }
        var before = TaskChange.snapshot(task);
        task.assign(actorId, AssignmentSource.SELF_CLAIM);
        return save(task, actorId, before);
    }

    public TaskView release(UUID spaceId, UUID actorId, UUID taskId) {
        TaskSpaceAccess.Role role = requireWriter(spaceId, actorId, taskId);
        Task task = lockedTask(spaceId, taskId);
        if (task.getAssigneeId() == null) {
            return TaskView.from(task);
        }
        if (role != TaskSpaceAccess.Role.OWNER
                && !(actorId.equals(task.getAssigneeId())
                        && task.getAssignmentSource() == AssignmentSource.SELF_CLAIM)) {
            throw new TaskAccessException(
                    "Only the self-claiming assignee or owner can release the task");
        }
        var before = TaskChange.snapshot(task);
        task.unassign();
        return save(task, actorId, before);
    }

    public TaskView assignDirect(UUID spaceId, UUID actorId, UUID taskId, UUID assigneeId) {
        TaskSpaceAccess.Role role = requireWriter(spaceId, actorId, taskId);
        if (role != TaskSpaceAccess.Role.OWNER) {
            throw new TaskAccessException("Only the owner can assign another user");
        }
        if (assigneeId == null) {
            throw new FieldValidationException(
                    "assigneeId", FieldErrorCode.REQUIRED, "Assignee is required");
        }
        Task task = lockedTask(spaceId, taskId);
        TaskSpaceAccess.Role assigneeRole = membership().roleOf(spaceId, assigneeId);
        if (assigneeRole != TaskSpaceAccess.Role.MEMBER
                && assigneeRole != TaskSpaceAccess.Role.OWNER) {
            throw new FieldValidationException(
                    "assigneeId",
                    FieldErrorCode.INVALID_REFERENCE,
                    "Assignee must be a member or owner");
        }
        var before = TaskChange.snapshot(task);
        task.assign(assigneeId, AssignmentSource.DIRECT);
        return save(task, actorId, before);
    }

    private TaskSpaceAccess.Role requireWriter(UUID spaceId, UUID actorId, UUID taskId) {
        if (spaceId == null || actorId == null || taskId == null) {
            throw new TaskValidationException("Identifiers are required");
        }
        statuses.lockInitialForRead(spaceId);
        TaskSpaceAccess.Role role = membership().roleOf(spaceId, actorId);
        if (role == null) {
            throw new TaskAccessException(
                    TaskAccessException.Reason.NOT_MEMBER, "Space membership is required");
        }
        if (role == TaskSpaceAccess.Role.GUEST) {
            throw new TaskAccessException("Assignment changes are not allowed");
        }
        return role;
    }

    private TaskSpaceAccess membership() {
        TaskSpaceAccess result = access.getIfAvailable();
        if (result == null) {
            throw new TaskDependencyException(
                    TaskDependencyException.Reason.SPACES_UNAVAILABLE,
                    "Spaces access adapter is not configured");
        }
        return result;
    }

    private Task lockedTask(UUID spaceId, UUID taskId) {
        return tasks.findForUpdateByIdAndSpaceId(taskId, spaceId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
    }

    private TaskView save(Task task, UUID actorId, Map<String, Object> before) {
        TaskChange change = TaskChange.updated(task, actorId, before);
        if (change.hasChanges()) {
            tasks.saveAndFlush(task);
            changes.saveAndFlush(change);
        }
        return TaskView.from(task);
    }
}
