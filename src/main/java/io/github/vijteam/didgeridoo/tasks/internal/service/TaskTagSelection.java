package io.github.vijteam.didgeridoo.tasks.internal.service;

import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.SpaceTag;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskTagRepository;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

final class TaskTagSelection {
    private TaskTagSelection() {}

    static List<SpaceTag> resolve(TaskTagRepository tags, UUID spaceId, List<UUID> tagIds) {
        var distinct = new HashSet<>(tagIds);
        if (distinct.contains(null) || distinct.size() != tagIds.size()) {
            throw new FieldValidationException(
                    "tagIds", FieldErrorCode.INVALID_FORMAT, "Tags must be distinct IDs");
        }
        var found = tags.byIds(spaceId, distinct);
        if (found.size() != distinct.size()) {
            throw new FieldValidationException(
                    "tagIds", FieldErrorCode.INVALID_REFERENCE, "Tags must belong to this space");
        }
        return found;
    }
}
