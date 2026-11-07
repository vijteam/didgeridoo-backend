package io.github.vijteam.didgeridoo.spaces.api;

import java.util.Optional;
import java.util.UUID;

public interface SpaceAccess {
    Optional<SpaceRole> findRole(UUID spaceId, UUID userId);

    void requireMember(UUID spaceId, UUID userId);

    void requireOwner(UUID spaceId, UUID userId);

    boolean isAssignable(UUID spaceId, UUID userId);
}
