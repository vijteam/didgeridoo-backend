package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;

import java.time.Instant;
import java.util.UUID;

public record TaskSearch(
        String text, UUID assigneeId, UUID statusId, Instant from, Instant until, Order order) {
    public TaskSearch {
        if (from != null && until != null && !from.isBefore(until)) {
            throw new TaskValidationException("Deadline start must be before the end");
        }
    }

    public enum Order {
        CREATED,
        DEADLINE,
        STATUS
    }
}
