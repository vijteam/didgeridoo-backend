package io.github.vijteam.didgeridoo.spaces.internal.service;

import io.github.vijteam.didgeridoo.spaces.api.SpaceAccess;
import io.github.vijteam.didgeridoo.spaces.api.SpaceAccessDeniedException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceNotFoundException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembershipId;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SpaceAccessService implements SpaceAccess {
    private final SpaceMembershipRepository memberships;

    public SpaceAccessService(SpaceMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public Optional<SpaceRole> findRole(UUID spaceId, UUID userId) {
        return memberships
                .findById(new SpaceMembershipId(spaceId, userId))
                .map(SpaceMembership::getRole);
    }

    @Override
    public void requireMember(UUID spaceId, UUID userId) {
        findRole(spaceId, userId).orElseThrow(SpaceNotFoundException::new);
    }

    @Override
    public void requireOwner(UUID spaceId, UUID userId) {
        SpaceRole role = findRole(spaceId, userId).orElseThrow(SpaceNotFoundException::new);
        if (role != SpaceRole.OWNER) {
            throw new SpaceAccessDeniedException();
        }
    }

    @Override
    public boolean isAssignable(UUID spaceId, UUID userId) {
        return findRole(spaceId, userId).filter(role -> role != SpaceRole.GUEST).isPresent();
    }
}
