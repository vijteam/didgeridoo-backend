package io.github.vijteam.didgeridoo.spaces.api;

import org.springframework.data.domain.Page;

import java.util.UUID;

/** Reads restricted to current members; filtering is applied before pagination. */
public interface SpaceQueries {
    SpaceSummary get(UUID actorId, UUID spaceId);

    Page<SpaceSummary> search(UUID actorId, String name, int page, int size);
}
