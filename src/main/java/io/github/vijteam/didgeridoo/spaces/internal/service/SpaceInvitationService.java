package io.github.vijteam.didgeridoo.spaces.internal.service;

import io.github.vijteam.didgeridoo.identity.api.UserDirectory;
import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.spaces.api.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SpaceInvitationService {
    private final InvitationStore store;
    private final SpaceAccess access;
    private final UserDirectory users;

    public SpaceInvitationService(InvitationStore store, SpaceAccess access, UserDirectory users) {
        this.store = store;
        this.access = access;
        this.users = users;
    }

    @Transactional
    public SpaceInvitation create(
            UUID actorId, UUID spaceId, UUID requestId, String username, SpaceRole role) {
        store.lockRequest(actorId, requestId);
        lockSpace(spaceId);
        access.requireOwner(spaceId, actorId);
        UUID target =
                users.findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new FieldValidationException(
                                                "username",
                                                FieldErrorCode.INVALID_REFERENCE,
                                                "Registered user not found"))
                        .id();
        var previous = store.findByRequest(actorId, requestId);
        if (previous.isPresent()) {
            SpaceInvitation invitation = previous.orElseThrow();
            if (!sameRequest(invitation, spaceId, target, role)) {
                throw new IdempotencyConflict();
            }
            return invitation;
        }
        if (store.isMember(spaceId, target)) {
            throw new InvitationConflictException("User is already a member");
        }
        if (store.hasPending(spaceId, target)) {
            throw new InvitationConflictException("Pending invitation already exists");
        }
        return store.create(spaceId, actorId, target, requestId, role);
    }

    @Transactional
    public SpaceInvitation decide(UUID actorId, UUID invitationId, InvitationState decision) {
        if (decision == InvitationState.PENDING || decision == null) {
            throw new IllegalArgumentException("Terminal decision required");
        }
        SpaceInvitation snapshot =
                store.find(invitationId).orElseThrow(SpaceNotFoundException::new);
        lockSpace(snapshot.spaceId());
        SpaceInvitation invitation =
                store.lock(invitationId).orElseThrow(SpaceNotFoundException::new);
        requireVisible(invitation, actorId);
        if (decision == InvitationState.CANCELLED) {
            if (access.findRole(invitation.spaceId(), actorId).orElse(null) != SpaceRole.OWNER) {
                throw new SpaceAccessDeniedException();
            }
        } else if (!invitation.inviteeId().equals(actorId)) {
            throw new SpaceAccessDeniedException();
        }
        if (invitation.state() == decision) {
            return invitation;
        }
        if (invitation.state() != InvitationState.PENDING) {
            throw new InvitationConflictException("Invitation already has a different decision");
        }
        if (decision == InvitationState.ACCEPTED) {
            if (store.isMember(invitation.spaceId(), actorId)) {
                throw new InvitationConflictException("User is already a member");
            }
            store.addMember(invitation.spaceId(), actorId, invitation.role());
        }
        return store.decide(invitationId, decision);
    }

    @Transactional(readOnly = true)
    public SpaceInvitation get(UUID actorId, UUID invitationId) {
        SpaceInvitation invitation =
                store.find(invitationId).orElseThrow(SpaceNotFoundException::new);
        requireVisible(invitation, actorId);
        return invitation;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InvitationStore.Page forSpace(UUID actorId, UUID spaceId, SpacePageWindow window) {
        access.requireOwner(spaceId, actorId);
        return store.forSpace(spaceId, window);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InvitationStore.Page forRecipient(UUID actorId, SpacePageWindow window) {
        return store.forRecipient(actorId, window);
    }

    private void lockSpace(UUID spaceId) {
        if (!store.lockSpace(spaceId)) {
            throw new SpaceNotFoundException();
        }
    }

    private void requireVisible(SpaceInvitation invitation, UUID actorId) {
        boolean recipient = invitation.inviteeId().equals(actorId);
        boolean owner =
                access.findRole(invitation.spaceId(), actorId)
                        .filter(role -> role == SpaceRole.OWNER)
                        .isPresent();
        if (!recipient && !owner) {
            throw new SpaceNotFoundException();
        }
    }

    private boolean sameRequest(
            SpaceInvitation invitation, UUID spaceId, UUID target, SpaceRole role) {
        return invitation.spaceId().equals(spaceId)
                && invitation.inviteeId().equals(target)
                && invitation.role() == role;
    }

    private static class IdempotencyConflict extends ApplicationException {
        IdempotencyConflict() {
            super(
                    ApiErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was used with a different invitation");
        }
    }
}
