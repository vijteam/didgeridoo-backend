package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.AssignmentSource;
import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskStatus;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class TaskCreationService {
    private final TaskRepository tasks;
    private final TaskStatusRepository statuses;
    private final TaskChangeRepository changes;
    private final ObjectProvider<TaskSpaceAccess> access;

    public TaskCreationService(
            TaskRepository tasks,
            TaskStatusRepository statuses,
            TaskChangeRepository changes,
            ObjectProvider<TaskSpaceAccess> access) {
        this.tasks = tasks;
        this.statuses = statuses;
        this.changes = changes;
        this.access = access;
    }

    /** actorId must come from the authenticated caller, never from an untrusted request body. */
    public UUID create(UUID spaceId, UUID actorId, CreateTask command) {
        if (spaceId == null || actorId == null || command == null) {
            throw new TaskValidationException("spaceId, actorId and command are required");
        }
        TaskSpaceAccess membership = access.getIfAvailable();
        if (membership == null) {
            throw new TaskDependencyException(
                    TaskDependencyException.Reason.SPACES_UNAVAILABLE,
                    "Spaces access adapter is not configured");
        }
        TaskSpaceAccess.Role role = membership.roleOf(spaceId, actorId);
        if (role == null) {
            throw new TaskAccessException(
                    TaskAccessException.Reason.NOT_MEMBER, "Space membership is required");
        }
        if (role != TaskSpaceAccess.Role.OWNER && role != TaskSpaceAccess.Role.MEMBER) {
            throw new TaskAccessException("Task creation is not allowed");
        }
        if (command.assigneeId() != null) {
            if (role != TaskSpaceAccess.Role.OWNER && !actorId.equals(command.assigneeId())) {
                throw new TaskAccessException("Members can only assign themselves");
            }
            TaskSpaceAccess.Role assigneeRole = membership.roleOf(spaceId, command.assigneeId());
            if (assigneeRole != TaskSpaceAccess.Role.OWNER
                    && assigneeRole != TaskSpaceAccess.Role.MEMBER) {
                throw new TaskValidationException(
                        "assigneeId", "Assignee must be a member or owner");
            }
        }
        TaskStatus status =
                command.statusId() == null
                        ? statuses.findFirstBySpaceIdAndKindOrderByPositionAsc(
                                        spaceId, StatusKind.INITIAL)
                                .orElseThrow(
                                        () ->
                                                new TaskDependencyException(
                                                        TaskDependencyException.Reason
                                                                .INITIAL_STATUS_MISSING,
                                                        "Initial status is missing"))
                        : statuses.findByIdAndSpaceId(command.statusId(), spaceId)
                                .orElseThrow(
                                        () ->
                                                new TaskValidationException(
                                                        "statusId",
                                                        "Status does not belong to this space"));
        Task task = new Task(spaceId, actorId, status.getId(), command.title());
        task.updateDetails(
                command.title(),
                command.description(),
                command.deadline(),
                command.priority() == null ? TaskPriority.MEDIUM : command.priority(),
                command.assigneeCanEdit());
        if (command.assigneeId() != null) {
            task.assign(command.assigneeId(), AssignmentSource.DIRECT);
        }
        tasks.saveAndFlush(task);
        changes.saveAndFlush(TaskChange.created(task, actorId));
        return task.getId();
    }
}
