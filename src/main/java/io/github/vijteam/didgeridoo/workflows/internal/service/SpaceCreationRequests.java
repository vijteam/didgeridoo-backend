package io.github.vijteam.didgeridoo.workflows.internal.service;

import java.util.UUID;

/** Transaction-scoped reservation: lock must remain held until workflow commit or rollback. */
public interface SpaceCreationRequests {
    record Reservation(String name, UUID spaceId) {}

    Reservation reserve(UUID actorId, UUID requestId, String name);

    void complete(UUID actorId, UUID requestId, UUID spaceId);
}
