package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.AssignmentSource;
import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;

import java.time.Instant;
import java.util.UUID;

/** Immutable snapshot; persistence entities stay inside tasks. */
public record TaskView(
        UUID id,
        UUID spaceId,
        UUID creatorId,
        UUID statusId,
        String title,
        String description,
        Instant deadline,
        TaskPriority priority,
        boolean assigneeCanEdit,
        UUID assigneeId,
        AssignmentSource assignmentSource,
        Instant createdAt,
        Instant updatedAt) {
    static TaskView from(Task task) {
        return new TaskView(
                task.getId(),
                task.getSpaceId(),
                task.getCreatorId(),
                task.getStatusId(),
                task.getTitle(),
                task.getDescription(),
                task.getDeadline(),
                task.getPriority(),
                task.isAssigneeCanEdit(),
                task.getAssigneeId(),
                task.getAssignmentSource(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
