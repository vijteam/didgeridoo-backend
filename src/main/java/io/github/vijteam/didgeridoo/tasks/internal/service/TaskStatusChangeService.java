package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class TaskStatusChangeService {
    private final TaskRepository tasks;
    private final TaskStatusRepository statuses;
    private final TaskChangeRepository changes;
    private final ObjectProvider<TaskSpaceAccess> access;

    public TaskStatusChangeService(
            TaskRepository tasks,
            TaskStatusRepository statuses,
            TaskChangeRepository changes,
            ObjectProvider<TaskSpaceAccess> access) {
        this.tasks = tasks;
        this.statuses = statuses;
        this.changes = changes;
        this.access = access;
    }

    /** actorId must come from the authenticated caller. Status changes preserve assignment. */
    public TaskView change(UUID spaceId, UUID actorId, UUID taskId, UUID statusId) {
        if (spaceId == null || actorId == null || taskId == null || statusId == null) {
            throw new IllegalArgumentException(
                    "spaceId, actorId, taskId and statusId are required");
        }
        TaskSpaceAccess membership = access.getIfAvailable();
        if (membership == null) {
            throw new IllegalStateException("Spaces access adapter is not configured");
        }
        TaskSpaceAccess.Role role = membership.roleOf(spaceId, actorId);
        if (role != TaskSpaceAccess.Role.MEMBER && role != TaskSpaceAccess.Role.OWNER) {
            throw new SecurityException("Status change is not allowed");
        }
        Task task =
                tasks.findForUpdateByIdAndSpaceId(taskId, spaceId)
                        .orElseThrow(() -> new NoSuchElementException("Task not found"));
        boolean allowed =
                role == TaskSpaceAccess.Role.OWNER
                        || actorId.equals(task.getCreatorId())
                        || actorId.equals(task.getAssigneeId())
                        || task.getAssigneeId() == null;
        if (!allowed) {
            throw new SecurityException("Status change is not allowed");
        }
        statuses.findByIdAndSpaceId(statusId, spaceId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Status does not belong to this space"));
        if (statusId.equals(task.getStatusId())) {
            return TaskView.from(task);
        }
        var before = TaskChange.snapshot(task);
        task.changeStatus(statusId);
        tasks.saveAndFlush(task);
        changes.saveAndFlush(TaskChange.updated(task, actorId, before));
        return TaskView.from(task);
    }
}
