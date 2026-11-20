package io.github.vijteam.didgeridoo.spaces.internal.service;

import io.github.vijteam.didgeridoo.identity.api.UserDirectory;
import io.github.vijteam.didgeridoo.identity.api.UserSummary;
import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;
import io.github.vijteam.didgeridoo.spaces.api.*;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembershipId;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceMembershipRepository;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SpaceMemberDirectory implements SpaceMembers {
    private final SpaceAccess access;
    private final UserDirectory users;
    private final MemberDirectoryStore members;
    private final SpaceRepository spaces;
    private final SpaceMembershipRepository memberships;

    public SpaceMemberDirectory(
            SpaceAccess access,
            UserDirectory users,
            MemberDirectoryStore members,
            SpaceRepository spaces,
            SpaceMembershipRepository memberships) {
        this.access = access;
        this.users = users;
        this.members = members;
        this.spaces = spaces;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(UUID actorId, UUID spaceId, SpacePageWindow window) {
        access.requireMember(spaceId, actorId);
        MemberDirectoryStore.Page page = members.list(spaceId, window);
        Map<UUID, UserSummary> summaries =
                users.findByIds(
                        page.entries().stream()
                                .map(MemberDirectoryStore.Entry::userId)
                                .collect(Collectors.toSet()));
        var items =
                page.entries().stream()
                        .map(
                                entry ->
                                        summary(
                                                summaries.get(entry.userId()),
                                                entry.role(),
                                                entry.joinedAt()))
                        .toList();
        return new Page(items, window.page(), window.size(), page.total());
    }

    @Override
    @Transactional
    public SpaceMemberSummary changeRole(UUID actorId, UUID spaceId, UUID userId, SpaceRole role) {
        spaces.lockById(spaceId).orElseThrow(SpaceNotFoundException::new);
        access.requireOwner(spaceId, actorId);
        var membership =
                memberships
                        .findById(new SpaceMembershipId(spaceId, userId))
                        .orElseThrow(SpaceNotFoundException::new);
        SpaceRole current = membership.getRole();
        if (current != role) {
            if (current == SpaceRole.OWNER
                    && memberships.countByIdSpaceIdAndRole(spaceId, SpaceRole.OWNER) == 1) {
                throw new MembershipRoleConflict(
                        ApiErrorCode.LAST_OWNER, "Space must retain an owner");
            }
            if (role == SpaceRole.GUEST) {
                throw new MembershipRoleConflict(
                        ApiErrorCode.MEMBERSHIP_CHANGE_UNAVAILABLE,
                        "Guest demotion requires atomic assignment removal");
            }
            membership.changeRole(role);
        }
        return summary(users.findByIds(Set.of(userId)).get(userId), role, membership.getJoinedAt());
    }

    private SpaceMemberSummary summary(UserSummary user, SpaceRole role, Instant joinedAt) {
        if (user == null) {
            throw new IllegalStateException("Membership references missing user");
        }
        return new SpaceMemberSummary(
                user.id(), user.username(), user.displayName(), user.avatarUrl(), role, joinedAt);
    }

    private static class MembershipRoleConflict extends ApplicationException {
        MembershipRoleConflict(ApiErrorCode code, String message) {
            super(code, message);
        }
    }
}
