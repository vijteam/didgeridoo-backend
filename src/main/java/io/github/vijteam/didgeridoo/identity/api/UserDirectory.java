package io.github.vijteam.didgeridoo.identity.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserDirectory {
    Optional<UserSummary> findById(UUID id);

    Optional<UserSummary> findByUsername(String username);

    Map<UUID, UserSummary> findByIds(Set<UUID> ids);
}
