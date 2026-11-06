package io.github.vijteam.didgeridoo.spaces.internal.service;

import io.github.vijteam.didgeridoo.identity.api.UserDirectory;
import io.github.vijteam.didgeridoo.spaces.api.SpaceAccess;
import io.github.vijteam.didgeridoo.spaces.api.SpaceNotFoundException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.api.SpaceSummary;
import io.github.vijteam.didgeridoo.spaces.internal.model.Space;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceMembershipRepository;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SpaceService {
    private final SpaceRepository spaces;
    private final SpaceMembershipRepository memberships;
    private final UserDirectory users;
    private final SpaceAccess access;

    public SpaceService(
            SpaceRepository spaces,
            SpaceMembershipRepository memberships,
            UserDirectory users,
            SpaceAccess access) {
        this.spaces = spaces;
        this.memberships = memberships;
        this.users = users;
        this.access = access;
    }

    @Transactional
    public SpaceSummary createWithOwner(UUID actorId, String name) {
        requireActor(actorId);
        Space space = new Space(name);

        if (users.findById(actorId).isEmpty()) {
            throw new IllegalArgumentException("Creator does not exist");
        }

        Space saved = spaces.saveAndFlush(space);
        memberships.saveAndFlush(new SpaceMembership(saved.getId(), actorId, SpaceRole.OWNER));

        return toSummary(saved);
    }

    public SpaceSummary get(UUID actorId, UUID spaceId) {
        requireActor(actorId);

        if (spaceId == null) {
            throw new IllegalArgumentException("Space identifier is required");
        }

        access.requireMember(spaceId, actorId);
        return spaces.findById(spaceId)
                .map(SpaceService::toSummary)
                .orElseThrow(SpaceNotFoundException::new);
    }

    public Page<SpaceSummary> search(UUID actorId, String name, int page, int size) {
        requireActor(actorId);

        if (name == null || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Search name and valid pagination are required");
        }

        PageRequest request = PageRequest.of(page, size, Sort.by("name", "id"));
        return spaces.findByMemberUserIdAndNameContainingIgnoreCase(actorId, name, request)
                .map(SpaceService::toSummary);
    }

    private static void requireActor(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("Actor identifier is required");
        }
    }

    private static SpaceSummary toSummary(Space space) {
        return new SpaceSummary(
                space.getId(), space.getName(), space.getCreatedAt(), space.getUpdatedAt());
    }
}
