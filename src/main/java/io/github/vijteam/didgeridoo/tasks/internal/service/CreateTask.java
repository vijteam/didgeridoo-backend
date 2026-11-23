package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateTask(
        String title,
        String description,
        Instant deadline,
        TaskPriority priority,
        UUID statusId,
        UUID assigneeId,
        boolean assigneeCanEdit,
        List<UUID> tagIds) {
    public CreateTask(
            String title,
            String description,
            Instant deadline,
            TaskPriority priority,
            UUID statusId,
            UUID assigneeId,
            boolean assigneeCanEdit) {
        this(
                title,
                description,
                deadline,
                priority,
                statusId,
                assigneeId,
                assigneeCanEdit,
                List.of());
    }
}
