package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskReadingService {
    private final TaskRepository tasks;
    private final ObjectProvider<TaskSpaceAccess> access;

    public TaskReadingService(TaskRepository tasks, ObjectProvider<TaskSpaceAccess> access) {
        this.tasks = tasks;
        this.access = access;
    }

    /** actorId must come from the authenticated caller. */
    public TaskView get(UUID spaceId, UUID actorId, UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId is required");
        }
        requireMembership(spaceId, actorId);
        return tasks.findByIdAndSpaceId(taskId, spaceId)
                .map(TaskView::from)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
    }

    public Page<TaskView> list(UUID spaceId, UUID actorId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "page must be non-negative and size must be between 1 and 100");
        }
        requireMembership(spaceId, actorId);
        return tasks.findAllBySpaceId(
                        spaceId, PageRequest.of(page, size, Sort.by("createdAt", "id")))
                .map(TaskView::from);
    }

    private void requireMembership(UUID spaceId, UUID actorId) {
        if (spaceId == null || actorId == null) {
            throw new IllegalArgumentException("spaceId and actorId are required");
        }
        TaskSpaceAccess membership = access.getIfAvailable();
        if (membership == null) {
            throw new IllegalStateException("Spaces access adapter is not configured");
        }
        if (membership.roleOf(spaceId, actorId) == null) {
            throw new SecurityException("Task reading is not allowed");
        }
    }
}
