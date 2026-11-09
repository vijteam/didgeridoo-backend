package io.github.vijteam.didgeridoo.tasks.api;

import java.util.UUID;

/** Internal module API for the create-space workflow, not an authorization boundary. */
public interface StatusInitializer {
    /**
     * Creates INITIAL and FINAL for a newly created space in the caller's transaction. The caller
     * creates the space and checks access. Existing statuses cause rejection.
     */
    void initialize(UUID spaceId);
}
