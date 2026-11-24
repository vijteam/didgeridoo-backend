package io.github.vijteam.didgeridoo.identity.internal.dto;

public record RegisterRequest(
    String username,
    String displayName,
    String email,
    String password
) {}
