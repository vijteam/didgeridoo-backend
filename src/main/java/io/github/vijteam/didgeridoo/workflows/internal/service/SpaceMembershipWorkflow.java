package io.github.vijteam.didgeridoo.workflows.internal.service;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceAccess;
import io.github.vijteam.didgeridoo.spaces.api.SpaceMemberSummary;
import io.github.vijteam.didgeridoo.spaces.api.SpaceMembershipLifecycle;
import io.github.vijteam.didgeridoo.spaces.api.SpaceNotFoundException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.tasks.api.TaskSpaceLifecycle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class SpaceMembershipWorkflow {
    public enum DepartureOutcome {
        LEFT,
        TRANSFERRED,
        SPACE_DELETED
    }

    public record Departure(UUID spaceId, DepartureOutcome outcome, UUID successorId) {}

    private final SpaceAccess access;
    private final SpaceMembershipLifecycle members;
    private final TaskSpaceLifecycle tasks;
    private final SpaceDepartureRequests departures;

    public SpaceMembershipWorkflow(
            SpaceAccess access,
            SpaceMembershipLifecycle members,
            TaskSpaceLifecycle tasks,
            SpaceDepartureRequests departures) {
        this.access = access;
        this.members = members;
        this.tasks = tasks;
        this.departures = departures;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public long assignmentImpact(UUID actor, UUID spaceId, UUID userId) {
        access.requireOwner(spaceId, actor);
        role(spaceId, userId);
        return tasks.countAssignments(spaceId, userId);
    }

    @Transactional
    public SpaceMemberSummary updateRole(
            UUID actor, UUID spaceId, UUID userId, SpaceRole role, Long affectedCount) {
        members.lockSpace(spaceId);
        access.requireOwner(spaceId, actor);
        SpaceRole current = role(spaceId, userId);
        if (current == SpaceRole.OWNER && role != SpaceRole.OWNER && lastOwner(spaceId)) {
            throw lastOwnerConflict();
        }
        if (role == SpaceRole.GUEST && current != SpaceRole.GUEST) {
            if (affectedCount == null) {
                throw new FieldValidationException(
                        "affectedCount", FieldErrorCode.REQUIRED, "Confirm the assignment count");
            }
            if (tasks.clearAssignments(spaceId, userId, actor) != affectedCount) {
                throw new MembershipConflict(
                        ApiErrorCode.MEMBERSHIP_CHANGE_CONFLICT, "Assignment count changed");
            }
        }
        return members.changeRole(actor, spaceId, userId, role);
    }

    @Transactional
    public void exclude(UUID actor, UUID spaceId, UUID userId) {
        members.lockSpace(spaceId);
        access.requireOwner(spaceId, actor);
        role(spaceId, userId);
        if (actor.equals(userId)) {
            throw new FieldValidationException(
                    "userId", FieldErrorCode.INVALID_REFERENCE, "Use departure to leave a space");
        }
        removeMembership(actor, spaceId, userId);
    }

    @Transactional
    public Departure depart(
            UUID actor,
            UUID spaceId,
            UUID requestId,
            UUID successor,
            boolean deleteSpace,
            boolean allowAutomaticDeletion) {
        departures.lock(actor, requestId);
        var previous = departures.find(actor, requestId);
        if (previous.isPresent()) {
            var stored = previous.orElseThrow();
            if (!stored.result().spaceId().equals(spaceId)
                    || !Objects.equals(stored.requestedSuccessor(), successor)
                    || stored.requestedDeletion() != deleteSpace
                    || stored.allowAutomaticDeletion() != allowAutomaticDeletion) {
                throw new MembershipConflict(
                        ApiErrorCode.IDEMPOTENCY_CONFLICT,
                        "Request key was used for a different departure");
            }
            return stored.result();
        }

        members.lockSpace(spaceId);
        Departure result = leave(actor, spaceId, successor, deleteSpace, allowAutomaticDeletion);
        departures.save(actor, requestId, successor, deleteSpace, allowAutomaticDeletion, result);
        return result;
    }

    private Departure leave(
            UUID actor,
            UUID spaceId,
            UUID successor,
            boolean deleteSpace,
            boolean allowAutomaticDeletion) {
        if (role(spaceId, actor) != SpaceRole.OWNER || !lastOwner(spaceId)) {
            if (successor != null || deleteSpace) {
                throw new MembershipConflict(
                        ApiErrorCode.MEMBERSHIP_CHANGE_CONFLICT,
                        "Departure needs no ownership decision");
            }
            removeMembership(actor, spaceId, actor);
            return new Departure(spaceId, DepartureOutcome.LEFT, null);
        }
        if (successor != null) {
            if (access.findRole(spaceId, successor).orElse(null) != SpaceRole.MEMBER) {
                throw new FieldValidationException(
                        "successorId",
                        FieldErrorCode.INVALID_REFERENCE,
                        "Successor must be a member");
            }
            members.changeRole(actor, spaceId, successor, SpaceRole.OWNER);
            removeMembership(actor, spaceId, actor);
            return new Departure(spaceId, DepartureOutcome.TRANSFERRED, successor);
        }
        if (!deleteSpace && members.count(spaceId, SpaceRole.MEMBER) != 0) {
            throw lastOwnerConflict();
        }
        if (!deleteSpace && !allowAutomaticDeletion) {
            throw new MembershipConflict(
                    ApiErrorCode.MEMBERSHIP_CHANGE_CONFLICT, "Confirm deletion of the space");
        }
        tasks.deleteSpaceData(spaceId);
        members.deleteSpace(spaceId);
        return new Departure(spaceId, DepartureOutcome.SPACE_DELETED, null);
    }

    private void removeMembership(UUID actor, UUID spaceId, UUID userId) {
        tasks.clearAssignments(spaceId, userId, actor);
        members.remove(spaceId, userId);
    }

    private SpaceRole role(UUID spaceId, UUID userId) {
        return access.findRole(spaceId, userId).orElseThrow(SpaceNotFoundException::new);
    }

    private boolean lastOwner(UUID spaceId) {
        return members.count(spaceId, SpaceRole.OWNER) == 1;
    }

    private MembershipConflict lastOwnerConflict() {
        return new MembershipConflict(
                ApiErrorCode.LAST_OWNER,
                "The last owner must keep or transfer ownership, or delete the space");
    }

    private static class MembershipConflict extends ApplicationException {
        MembershipConflict(ApiErrorCode code, String message) {
            super(code, message);
        }
    }
}
