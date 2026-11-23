package io.github.vijteam.didgeridoo.tasks.api;

import java.util.UUID;

/** Task data operations for a trusted workflow that verifies space and member permissions. */
public interface TaskSpaceLifecycle {
    /** Returns the current number of tasks assigned to the user in this space. */
    long countAssignments(UUID spaceId, UUID userId);

    /**
     * Clears assignments and records history in the caller's transaction. The workflow must call
     * this before changing membership and keep the transaction open through that change.
     */
    long clearAssignments(UUID spaceId, UUID userId, UUID actorId);

    /**
     * Deletes tasks, their history and statuses in the caller's transaction. The workflow must
     * serialize the space lifecycle and delete the space within the same transaction.
     */
    void deleteSpaceData(UUID spaceId);
}
