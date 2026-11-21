package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class TaskDeletionService {
    private final TaskRepository tasks;
    private final ObjectProvider<TaskSpaceAccess> access;

    public TaskDeletionService(TaskRepository tasks, ObjectProvider<TaskSpaceAccess> access) {
        this.tasks = tasks;
        this.access = access;
    }

    /** actorId comes from authentication; the caller may obtain deletion confirmation in the UI. */
    public void delete(UUID spaceId, UUID actorId, UUID taskId) {
        if (spaceId == null || actorId == null || taskId == null) {
            throw new TaskValidationException("Identifiers are required");
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
        if (role != TaskSpaceAccess.Role.MEMBER && role != TaskSpaceAccess.Role.OWNER) {
            throw new TaskAccessException("Task deletion is not allowed");
        }
        Task task =
                tasks.findForUpdateByIdAndSpaceId(taskId, spaceId)
                        .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        if (role != TaskSpaceAccess.Role.OWNER && !actorId.equals(task.getCreatorId())) {
            throw new TaskAccessException("Only the creator or owner can delete the task");
        }
        tasks.delete(task);
        tasks.flush();
    }
}
