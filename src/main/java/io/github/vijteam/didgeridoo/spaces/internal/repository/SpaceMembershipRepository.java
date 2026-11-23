package io.github.vijteam.didgeridoo.spaces.internal.repository;

import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembershipId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceMembershipRepository
        extends JpaRepository<SpaceMembership, SpaceMembershipId> {
    @Query(
            "select m.id.spaceId from SpaceMembership m where m.id.userId = :userId order by m.id.spaceId")
    List<UUID> findSpaceIdsByUserId(UUID userId);

    Optional<SpaceMembership>
            findFirstByIdSpaceIdAndRoleAndIdUserIdNotOrderByJoinedAtAscIdUserIdAsc(
                    UUID spaceId, SpaceRole role, UUID userId);

    long countByIdSpaceIdAndRole(UUID spaceId, SpaceRole role);
}
