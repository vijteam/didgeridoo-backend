package io.github.vijteam.didgeridoo.spaces.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Membership operations within a workflow transaction. Lock the space before checking roles. */
public interface SpaceMembershipLifecycle {
    void lockSpace(UUID spaceId);

    /** Returns memberships in a consistent order for locking multiple spaces. */
    List<UUID> spacesForUser(UUID userId);

    /** Chooses the first MEMBER by join time and ID, then the first GUEST, under the space lock. */
    Optional<UUID> successor(UUID spaceId, UUID departingUserId);

    long count(UUID spaceId, SpaceRole role);

    /** The workflow must clear task assignments before demoting an assignee to GUEST. */
    SpaceMemberSummary changeRole(UUID actorId, UUID spaceId, UUID userId, SpaceRole role);

    /** The workflow must preserve an owner and clear assignments before removing membership. */
    void remove(UUID spaceId, UUID userId);

    /** The workflow must remove task data before deleting the space. */
    void deleteSpace(UUID spaceId);
}
