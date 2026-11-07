package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.tasks.internal.model.TaskChange;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Immutable history snapshot; old/new values are JSON scalars or null. */
public record TaskChangeView(
        UUID id,
        UUID taskId,
        UUID actorId,
        String kind,
        Map<String, Map<String, Object>> changedFields,
        Instant createdAt) {
    public TaskChangeView {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        changedFields.forEach(
                (field, values) ->
                        copy.put(field, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        changedFields = Collections.unmodifiableMap(copy);
    }

    static TaskChangeView from(TaskChange change) {
        return new TaskChangeView(
                change.getId(),
                change.getTaskId(),
                change.getActorId(),
                change.getKind(),
                change.getChangedFields(),
                change.getCreatedAt());
    }
}
