package io.github.vijteam.didgeridoo.identity.api;

import java.util.Objects;
import java.util.UUID;

public record UserSummary(UUID id, String username, String displayName, String avatarUrl) {
    public UserSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
    }
}
