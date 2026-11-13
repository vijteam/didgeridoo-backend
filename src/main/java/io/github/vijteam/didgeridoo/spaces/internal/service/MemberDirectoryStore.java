package io.github.vijteam.didgeridoo.spaces.internal.service;

import io.github.vijteam.didgeridoo.spaces.api.SpacePageWindow;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MemberDirectoryStore {
    record Entry(UUID userId, SpaceRole role, Instant joinedAt) {}

    record Page(List<Entry> entries, long total) {
        public Page {
            entries = List.copyOf(entries);
        }
    }

    Page list(UUID spaceId, SpacePageWindow window);
}
