package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskConflictException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskStatus;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskStatusManagementService {
    private final TaskStatusRepository statuses;
    private final TaskRepository tasks;
    private final TaskChangeRepository changes;
    private final TaskMembership membership;

    TaskStatusManagementService(
            TaskStatusRepository statuses,
            TaskRepository tasks,
            TaskChangeRepository changes,
            TaskMembership membership) {
        this.statuses = statuses;
        this.tasks = tasks;
        this.changes = changes;
        this.membership = membership;
    }

    @Transactional(readOnly = true)
    public List<TaskStatusView> list(UUID spaceId, UUID actorId) {
        membership.requireMember(spaceId, actorId);
        return views(statuses.findAllBySpaceIdOrderByPositionAsc(spaceId));
    }

    public TaskStatusView create(UUID spaceId, UUID actorId, String name) {
        lockCatalog(spaceId, actorId);
        TaskStatus last = statuses.findAllBySpaceIdOrderByPositionAsc(spaceId).getLast();
        if (last.getKind() != StatusKind.FINAL) {
            throw new TaskConflictException("Final status must be last");
        }
        int position = last.getPosition();
        var added = new TaskStatus(spaceId, name, StatusKind.INTERMEDIATE, position);
        last.moveTo(Math.incrementExact(position));
        statuses.flush();
        return TaskStatusView.from(statuses.saveAndFlush(added));
    }

    public TaskStatusView rename(UUID spaceId, UUID actorId, UUID statusId, String name) {
        lockCatalog(spaceId, actorId);
        TaskStatus status = find(spaceId, statusId);
        status.rename(name);
        return TaskStatusView.from(status);
    }

    public List<TaskStatusView> reorder(UUID spaceId, UUID actorId, List<UUID> orderedIds) {
        lockCatalog(spaceId, actorId);
        List<TaskStatus> current = statuses.findAllBySpaceIdOrderByPositionAsc(spaceId);
        var byId =
                current.stream().collect(Collectors.toMap(TaskStatus::getId, Function.identity()));
        if (orderedIds == null
                || orderedIds.size() != current.size()
                || !byId.keySet().equals(new HashSet<>(orderedIds))) {
            throw new TaskValidationException("Order must contain every status exactly once");
        }
        List<TaskStatus> ordered = orderedIds.stream().map(byId::get).toList();
        if (ordered.getFirst().getKind() != StatusKind.INITIAL
                || ordered.getLast().getKind() != StatusKind.FINAL) {
            throw new TaskValidationException("Initial and final statuses must stay at the ends");
        }

        // The immediate unique position constraint needs disjoint temporary positions.
        int position = current.getLast().getPosition();
        int temporaryPosition = position;
        for (int i = 0; i < ordered.size(); i++) {
            TaskStatus status = ordered.get(i);
            if (status.getPosition() != i) {
                temporaryPosition = Math.incrementExact(temporaryPosition);
                status.moveTo(temporaryPosition);
            }
        }
        if (temporaryPosition != position) {
            statuses.flush();
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).moveTo(i);
            }
            statuses.flush();
        }
        return views(ordered);
    }

    @Transactional(readOnly = true)
    public long deletionImpact(UUID spaceId, UUID actorId, UUID statusId) {
        membership.requireOwner(spaceId, actorId);
        requireIntermediate(find(spaceId, statusId));
        return tasks.countBySpaceIdAndStatusId(spaceId, statusId);
    }

    public void delete(UUID spaceId, UUID actorId, UUID statusId, long expectedAffectedTasks) {
        if (expectedAffectedTasks < 0) {
            throw new TaskValidationException(
                    "Expected number of affected tasks must not be negative");
        }
        TaskStatus initial = lockCatalog(spaceId, actorId);
        TaskStatus status = find(spaceId, statusId);
        requireIntermediate(status);
        var affected = tasks.findAllBySpaceIdAndStatusIdOrderById(spaceId, statusId);
        if (affected.size() != expectedAffectedTasks) {
            throw new TaskConflictException("Affected task count changed");
        }
        var events = new ArrayList<TaskChange>(affected.size());
        for (var task : affected) {
            var before = TaskChange.snapshot(task);
            task.changeStatus(initial.getId());
            events.add(TaskChange.updated(task, actorId, before));
        }
        if (!events.isEmpty()) {
            tasks.flush();
            changes.saveAllAndFlush(events);
        }
        statuses.delete(status);
        statuses.flush();
    }

    private TaskStatus lockCatalog(UUID spaceId, UUID actorId) {
        if (spaceId == null || actorId == null) {
            throw new TaskValidationException("spaceId and actorId are required");
        }
        var initial = statuses.lockInitialForWrite(spaceId);
        membership.requireOwner(spaceId, actorId);
        return initial.orElseThrow(
                () ->
                        new TaskDependencyException(
                                TaskDependencyException.Reason.INITIAL_STATUS_MISSING,
                                "Initial status is missing"));
    }

    private TaskStatus find(UUID spaceId, UUID statusId) {
        if (statusId == null) {
            throw new TaskValidationException("statusId is required");
        }
        return statuses.findByIdAndSpaceId(statusId, spaceId)
                .orElseThrow(() -> new TaskNotFoundException("Status not found"));
    }

    private void requireIntermediate(TaskStatus status) {
        if (status.getKind() != StatusKind.INTERMEDIATE) {
            throw new TaskConflictException("Only intermediate statuses can be deleted");
        }
    }

    private List<TaskStatusView> views(List<TaskStatus> ordered) {
        return ordered.stream().map(TaskStatusView::from).toList();
    }
}
