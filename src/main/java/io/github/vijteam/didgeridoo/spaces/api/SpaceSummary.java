package io.github.vijteam.didgeridoo.spaces.api;

import java.time.Instant;
import java.util.UUID;

public record SpaceSummary(UUID id, String name, Instant createdAt, Instant updatedAt) {}
