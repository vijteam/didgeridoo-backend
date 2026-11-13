package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskHistoryService {
    private final TaskRepository tasks;
    private final TaskChangeRepository changes;
    private final ObjectProvider<TaskSpaceAccess> access;

    public TaskHistoryService(
            TaskRepository tasks,
            TaskChangeRepository changes,
            ObjectProvider<TaskSpaceAccess> access) {
        this.tasks = tasks;
        this.changes = changes;
        this.access = access;
    }

    /** actorId must come from the authenticated caller. Guests may read history. */
    public Page<TaskChangeView> list(UUID spaceId, UUID actorId, UUID taskId, int page, int size) {
        if (spaceId == null
                || actorId == null
                || taskId == null
                || page < 0
                || size < 1
                || size > 100) {
            throw new TaskValidationException(
                    "Identifiers, non-negative page and size between 1 and 100 are required");
        }
        TaskSpaceAccess membership = access.getIfAvailable();
        if (membership == null) {
            throw new TaskDependencyException(
                    TaskDependencyException.Reason.SPACES_UNAVAILABLE,
                    "Spaces access adapter is not configured");
        }
        if (membership.roleOf(spaceId, actorId) == null) {
            throw new TaskAccessException(
                    TaskAccessException.Reason.NOT_MEMBER, "History reading is not allowed");
        }
        tasks.findByIdAndSpaceId(taskId, spaceId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        return changes.findAllByTaskId(
                        taskId, PageRequest.of(page, size, Sort.by("createdAt", "id")))
                .map(TaskChangeView::from);
    }
}
