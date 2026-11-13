package io.github.vijteam.didgeridoo.spaces.api;

import java.util.UUID;

/** Public creation operation for the workflow coordinating space and default statuses. */
public interface SpaceCreation {
    /**
     * Creates the space and its OWNER membership in the caller's transaction, or starts a
     * transaction if none exists. The caller must supply a trusted authenticated actor identifier.
     */
    SpaceSummary createWithOwner(UUID actorId, String name);
}
