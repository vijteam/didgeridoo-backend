package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;

@Service
@Transactional
public class TaskEditingService {
    private final TaskRepository tasks;
    private final TaskChangeRepository changes;
    private final ObjectProvider<TaskSpaceAccess> access;

    public TaskEditingService(
            TaskRepository tasks,
            TaskChangeRepository changes,
            ObjectProvider<TaskSpaceAccess> access) {
        this.tasks = tasks;
        this.changes = changes;
        this.access = access;
    }

    /** actorId comes from the authenticated caller; this is not a PATCH contract. */
    public TaskView edit(UUID spaceId, UUID actorId, UUID taskId, EditTask command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        return update(spaceId, actorId, taskId, task -> command);
    }

    /** Merges only supplied fields after locking the current task inside this transaction. */
    public TaskView patch(UUID spaceId, UUID actorId, UUID taskId, TaskPatch patch) {
        if (patch == null) {
            throw new IllegalArgumentException("patch is required");
        }
        return update(spaceId, actorId, taskId, patch::applyTo);
    }

    private TaskView update(
            UUID spaceId, UUID actorId, UUID taskId, Function<Task, EditTask> merge) {
        if (spaceId == null || actorId == null || taskId == null) {
            throw new IllegalArgumentException("spaceId, actorId and taskId are required");
        }
        TaskSpaceAccess membership = access.getIfAvailable();
        if (membership == null) {
            throw new IllegalStateException("Spaces access adapter is not configured");
        }
        TaskSpaceAccess.Role role = membership.roleOf(spaceId, actorId);
        if (role != TaskSpaceAccess.Role.MEMBER && role != TaskSpaceAccess.Role.OWNER) {
            throw new SecurityException("Task editing is not allowed");
        }
        Task task =
                tasks.findForUpdateByIdAndSpaceId(taskId, spaceId)
                        .orElseThrow(() -> new NoSuchElementException("Task not found"));
        boolean manages = role == TaskSpaceAccess.Role.OWNER || actorId.equals(task.getCreatorId());
        if (!manages && !(actorId.equals(task.getAssigneeId()) && task.isAssigneeCanEdit())) {
            throw new SecurityException("Task editing is not allowed");
        }
        EditTask command = merge.apply(task);
        boolean canEdit =
                command.assigneeCanEdit() == null
                        ? task.isAssigneeCanEdit()
                        : command.assigneeCanEdit();
        if (!manages && canEdit != task.isAssigneeCanEdit()) {
            throw new SecurityException("Only the creator or owner can change editing permission");
        }
        var before = TaskChange.snapshot(task);
        task.updateDetails(
                command.title(),
                command.description(),
                command.deadline(),
                command.priority(),
                canEdit);
        TaskChange change = TaskChange.updated(task, actorId, before);
        if (change.hasChanges()) {
            tasks.saveAndFlush(task);
            changes.saveAndFlush(change);
        }
        return TaskView.from(task);
    }
}
