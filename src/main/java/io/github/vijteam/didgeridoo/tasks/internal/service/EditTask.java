package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;

import java.time.Instant;

/** Full content replacement; a null permission flag preserves its current value. */
public record EditTask(
        String title,
        String description,
        Instant deadline,
        TaskPriority priority,
        Boolean assigneeCanEdit) {}
