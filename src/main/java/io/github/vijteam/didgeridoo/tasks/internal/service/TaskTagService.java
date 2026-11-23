package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskConflictException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.SpaceTag;
import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskTagRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TaskTagService {
    private final TaskTagRepository tags;
    private final TaskRepository tasks;
    private final TaskStatusRepository statuses;
    private final TaskChangeRepository changes;
    private final TaskMembership membership;

    TaskTagService(
            TaskTagRepository tags,
            TaskRepository tasks,
            TaskStatusRepository statuses,
            TaskChangeRepository changes,
            TaskMembership membership) {
        this.tags = tags;
        this.tasks = tasks;
        this.statuses = statuses;
        this.changes = changes;
        this.membership = membership;
    }

    @Transactional(readOnly = true)
    public List<SpaceTag> list(UUID spaceId, UUID actorId) {
        membership.requireMember(spaceId, actorId);
        return tags.list(spaceId);
    }

    @Transactional(readOnly = true)
    public List<SpaceTag> forTask(UUID spaceId, UUID actorId, UUID taskId) {
        if (taskId == null) {
            throw new TaskValidationException("taskId is required");
        }
        membership.requireMember(spaceId, actorId);
        tasks.findByIdAndSpaceId(taskId, spaceId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        return tags.forTask(spaceId, taskId);
    }

    public SpaceTag create(UUID spaceId, UUID actorId, String name) {
        lockCatalog(spaceId, actorId);
        var tag = new SpaceTag(UUID.randomUUID(), name);
        requireAvailableName(spaceId, tag.name());
        tags.insert(spaceId, tag);
        return tag;
    }

    public SpaceTag rename(UUID spaceId, UUID actorId, UUID tagId, String name) {
        lockCatalog(spaceId, actorId);
        var current = find(spaceId, tagId);
        var tag = new SpaceTag(current.id(), name);
        if (!tag.name().equals(current.name())) {
            requireAvailableName(spaceId, tag.name());
            tags.rename(spaceId, tag);
        }
        return tag;
    }

    @Transactional(readOnly = true)
    public long deletionImpact(UUID spaceId, UUID actorId, UUID tagId) {
        membership.requireOwner(spaceId, actorId);
        find(spaceId, tagId);
        return tags.countTasks(spaceId, tagId);
    }

    public void delete(UUID spaceId, UUID actorId, UUID tagId, long expectedAffectedTasks) {
        if (expectedAffectedTasks < 0) {
            throw new TaskValidationException(
                    "Expected number of affected tasks must not be negative");
        }
        lockCatalog(spaceId, actorId);
        var tag = find(spaceId, tagId);
        var affected = tasks.lockAllWithTag(spaceId, tagId);
        if (affected.size() != expectedAffectedTasks) {
            throw new TaskConflictException("Affected task count changed");
        }

        var events = new ArrayList<TaskChange>(affected.size());
        for (Task task : affected) {
            TaskChange event = TaskChange.updated(task, actorId, TaskChange.snapshot(task));
            event.recordTagChange(tagId, tag.name(), null);
            task.markUpdated();
            events.add(event);
        }
        tags.delete(spaceId, tagId);
        changes.saveAllAndFlush(events);
    }

    public List<SpaceTag> replace(UUID spaceId, UUID actorId, UUID taskId, List<UUID> tagIds) {
        if (spaceId == null || taskId == null || tagIds == null) {
            throw new TaskValidationException("spaceId, taskId and tagIds are required");
        }
        statuses.lockInitialForRead(spaceId);
        var role = membership.requireMember(spaceId, actorId);
        if (role == TaskSpaceAccess.Role.GUEST) {
            throw new TaskAccessException("Task editing is not allowed");
        }
        Task task =
                tasks.findForUpdateByIdAndSpaceId(taskId, spaceId)
                        .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        if (!TaskEditingService.canEdit(task, actorId, role)) {
            throw new TaskAccessException("Task editing is not allowed");
        }
        var target = TaskTagSelection.resolve(tags, spaceId, tagIds);
        var previous = tags.forTask(spaceId, taskId);
        if (previous.equals(target)) {
            return List.copyOf(previous);
        }

        TaskChange event = TaskChange.updated(task, actorId, TaskChange.snapshot(task));
        var targetSet = new HashSet<>(target);
        var previousSet = new HashSet<>(previous);
        for (var tag : previous) {
            if (!targetSet.contains(tag)) {
                event.recordTagChange(tag.id(), tag.name(), null);
            }
        }
        for (var tag : target) {
            if (!previousSet.contains(tag)) {
                event.recordTagChange(tag.id(), null, tag.name());
            }
        }
        tags.replace(spaceId, taskId, target);
        task.markUpdated();
        changes.saveAndFlush(event);
        return List.copyOf(target);
    }

    private void lockCatalog(UUID spaceId, UUID actorId) {
        if (spaceId == null || actorId == null) {
            throw new TaskValidationException("spaceId and actorId are required");
        }
        var initial = statuses.lockInitialForWrite(spaceId);
        membership.requireOwner(spaceId, actorId);
        if (initial.isEmpty()) {
            throw new TaskDependencyException(
                    TaskDependencyException.Reason.INITIAL_STATUS_MISSING,
                    "Initial status is missing");
        }
    }

    private SpaceTag find(UUID spaceId, UUID tagId) {
        if (tagId == null) {
            throw new TaskValidationException("tagId is required");
        }
        return tags.find(spaceId, tagId)
                .orElseThrow(() -> new TaskNotFoundException("Tag not found"));
    }

    private void requireAvailableName(UUID spaceId, String name) {
        if (tags.nameTaken(spaceId, name)) {
            throw new TaskConflictException("Tag name is already used in this space");
        }
    }
}
