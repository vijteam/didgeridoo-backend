package io.github.vijteam.didgeridoo.spaces.internal.service;

import io.github.vijteam.didgeridoo.spaces.api.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Locks are held until the surrounding transaction finishes. Order: request, space, invitation. */
public interface InvitationStore {
    record Page(List<SpaceInvitation> items, long totalElements) {
        public Page {
            items = List.copyOf(items);
        }
    }

    void lockRequest(UUID actorId, UUID requestId);

    boolean lockSpace(UUID spaceId);

    Optional<SpaceInvitation> findByRequest(UUID actorId, UUID requestId);

    Optional<SpaceInvitation> find(UUID invitationId);

    Optional<SpaceInvitation> lock(UUID invitationId);

    boolean isMember(UUID spaceId, UUID userId);

    boolean hasPending(UUID spaceId, UUID userId);

    SpaceInvitation create(
            UUID spaceId, UUID inviterId, UUID inviteeId, UUID requestId, SpaceRole role);

    SpaceInvitation decide(UUID invitationId, InvitationState state);

    void addMember(UUID spaceId, UUID userId, SpaceRole role);

    Page forSpace(UUID spaceId, SpacePageWindow window);

    Page forRecipient(UUID actorId, SpacePageWindow window);
}
