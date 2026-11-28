package io.github.vijteam.didgeridoo.workflows.internal.service;

import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceMembershipWorkflow.Departure;

import java.util.Optional;
import java.util.UUID;

public interface SpaceDepartureRequests {
    record Recorded(
            UUID requestedSuccessor,
            boolean requestedDeletion,
            boolean allowAutomaticDeletion,
            Departure result) {}

    void lock(UUID actor, UUID requestId);

    Optional<Recorded> find(UUID actor, UUID requestId);

    void save(
            UUID actor,
            UUID requestId,
            UUID successor,
            boolean deleteSpace,
            boolean allowAutomaticDeletion,
            Departure result);
}
