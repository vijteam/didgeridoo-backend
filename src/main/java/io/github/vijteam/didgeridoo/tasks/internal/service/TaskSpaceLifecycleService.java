package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.api.TaskSpaceLifecycle;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskTagRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
class TaskSpaceLifecycleService implements TaskSpaceLifecycle {
    private final TaskRepository tasks;
    private final TaskStatusRepository statuses;
    private final TaskChangeRepository changes;
    private final TaskTagRepository tags;

    TaskSpaceLifecycleService(
            TaskRepository tasks,
            TaskStatusRepository statuses,
            TaskChangeRepository changes,
            TaskTagRepository tags) {
        this.tasks = tasks;
        this.statuses = statuses;
        this.changes = changes;
        this.tags = tags;
    }

    @Override
    @Transactional(readOnly = true)
    public long countAssignments(UUID spaceId, UUID userId) {
        if (spaceId == null || userId == null) {
            throw new TaskValidationException("spaceId and userId are required");
        }
        return tasks.countBySpaceIdAndAssigneeId(spaceId, userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long clearAssignments(UUID spaceId, UUID userId, UUID actorId) {
        if (spaceId == null || userId == null || actorId == null) {
            throw new TaskValidationException("spaceId, userId and actorId are required");
        }
        // Assignment writers share this lock and check membership after acquiring it.
        statuses.lockInitialForWrite(spaceId);
        var affected = tasks.findAllBySpaceIdAndAssigneeIdOrderById(spaceId, userId);
        var events = new ArrayList<TaskChange>(affected.size());
        for (var task : affected) {
            var before = TaskChange.snapshot(task);
            task.unassign();
            events.add(TaskChange.updated(task, actorId, before));
        }
        if (!events.isEmpty()) {
            tasks.flush();
            changes.saveAllAndFlush(events);
        }
        return affected.size();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteSpaceData(UUID spaceId) {
        if (spaceId == null) {
            throw new TaskValidationException("spaceId is required");
        }
        statuses.lockInitialForWrite(spaceId);
        // PostgreSQL cascades task deletion to history. Statuses can then be deleted.
        tasks.deleteSpaceTasks(spaceId);
        tags.deleteSpaceTags(spaceId);
        statuses.deleteSpaceStatuses(spaceId);
    }
}
