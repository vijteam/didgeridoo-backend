package io.github.vijteam.didgeridoo.spaces.internal.service;

import io.github.vijteam.didgeridoo.identity.api.UserDirectory;
import io.github.vijteam.didgeridoo.identity.api.UserSummary;
import io.github.vijteam.didgeridoo.spaces.api.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SpaceMemberDirectory implements SpaceMembers {
    private final SpaceAccess access;
    private final UserDirectory users;
    private final MemberDirectoryStore members;

    public SpaceMemberDirectory(
            SpaceAccess access, UserDirectory users, MemberDirectoryStore members) {
        this.access = access;
        this.users = users;
        this.members = members;
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
                        .map(entry -> summary(entry, summaries.get(entry.userId())))
                        .toList();
        return new Page(items, window.page(), window.size(), page.total());
    }

    private SpaceMemberSummary summary(MemberDirectoryStore.Entry entry, UserSummary user) {
        if (user == null) {
            throw new IllegalStateException("Membership references missing user");
        }
        return new SpaceMemberSummary(
                user.id(),
                user.username(),
                user.displayName(),
                user.avatarUrl(),
                entry.role(),
                entry.joinedAt());
    }
}
