package io.github.vijteam.didgeridoo.tasks.internal.dto;

/** Tasks-scoped response matching the shared ApiError shape until infrastructure supplies it. */
public record TaskApiError(String code, String message) {}
