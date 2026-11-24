package io.github.vijteam.didgeridoo.identity.internal.dto;

import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String displayName,
    String email,
    String avatarUrl
) {}
