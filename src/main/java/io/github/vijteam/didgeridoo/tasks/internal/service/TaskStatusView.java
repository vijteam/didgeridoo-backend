package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskStatus;

import java.util.UUID;

public record TaskStatusView(UUID id, String name, StatusKind kind, int position) {
    static TaskStatusView from(TaskStatus status) {
        return new TaskStatusView(
                status.getId(), status.getName(), status.getKind(), status.getPosition());
    }
}
