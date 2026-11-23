package io.github.vijteam.didgeridoo.tasks.internal.model;

import java.util.UUID;

public record SpaceTag(UUID id, String name) {
    public SpaceTag {
        name = TaskFieldValidator.text(name, 100, "name");
    }
}
