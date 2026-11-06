package io.github.vijteam.didgeridoo.tasks.internal.service;

import java.util.UUID;

/** Consumer-owned port; an adapter to the spaces API must supply current membership. */
public interface TaskSpaceAccess {
    /** Returns null for a non-member or an unavailable space. */
    Role roleOf(UUID spaceId, UUID userId);

    enum Role {
        OWNER,
        MEMBER,
        GUEST
    }
}
