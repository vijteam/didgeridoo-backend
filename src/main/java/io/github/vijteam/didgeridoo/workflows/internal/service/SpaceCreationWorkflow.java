package io.github.vijteam.didgeridoo.workflows.internal.service;

import io.github.vijteam.didgeridoo.spaces.api.SpaceCreation;
import io.github.vijteam.didgeridoo.spaces.api.SpaceName;
import io.github.vijteam.didgeridoo.spaces.api.SpaceQueries;
import io.github.vijteam.didgeridoo.spaces.api.SpaceSummary;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SpaceCreationWorkflow {
    private final SpaceCreation spaces;
    private final SpaceQueries queries;
    private final StatusInitializer statuses;
    private final SpaceCreationRequests requests;

    public SpaceCreationWorkflow(
            SpaceCreation spaces,
            SpaceQueries queries,
            StatusInitializer statuses,
            SpaceCreationRequests requests) {
        this.spaces = spaces;
        this.queries = queries;
        this.statuses = statuses;
        this.requests = requests;
    }

    @Transactional
    public SpaceSummary create(UUID actorId, UUID requestId, String name) {
        if (actorId == null || requestId == null) {
            throw new IllegalArgumentException("Actor and request identifiers are required");
        }
        String normalizedName = new SpaceName(name).value();
        var reservation = requests.reserve(actorId, requestId, normalizedName);
        if (!reservation.name().equals(normalizedName)) {
            throw new SpaceCreationConflictException();
        }
        if (reservation.spaceId() != null) {
            return queries.get(actorId, reservation.spaceId());
        }
        SpaceSummary space = spaces.createWithOwner(actorId, normalizedName);
        statuses.initialize(space.id());
        requests.complete(actorId, requestId, space.id());
        return space;
    }
}
