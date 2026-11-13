package io.github.vijteam.didgeridoo.spaces.api;

import java.time.Instant;
import java.util.UUID;

public record SpaceInvitation(
        UUID id,
        UUID spaceId,
        String spaceName,
        UUID inviteeId,
        UUID inviterId,
        SpaceRole role,
        InvitationState state,
        Instant createdAt,
        Instant updatedAt) {}
