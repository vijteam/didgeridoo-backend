package io.github.vijteam.didgeridoo.spaces.api;

import java.util.List;
import java.util.UUID;

public interface SpaceMembers {
    record Page(List<SpaceMemberSummary> items, int page, int size, long totalElements) {
        public Page {
            items = List.copyOf(items);
        }
    }

    Page list(UUID actorId, UUID spaceId, SpacePageWindow window);
}
