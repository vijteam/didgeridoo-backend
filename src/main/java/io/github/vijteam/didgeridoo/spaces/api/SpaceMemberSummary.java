package io.github.vijteam.didgeridoo.spaces.api;

import java.time.Instant;
import java.util.UUID;

public record SpaceMemberSummary(
        UUID userId,
        String username,
        String displayName,
        String avatarUrl,
        SpaceRole role,
        Instant joinedAt) {}
