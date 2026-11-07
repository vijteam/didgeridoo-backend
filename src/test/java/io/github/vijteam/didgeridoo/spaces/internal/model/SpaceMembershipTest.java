package io.github.vijteam.didgeridoo.spaces.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

class SpaceMembershipTest {

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void shouldCreateMembershipWithEachRole(SpaceRole role) {
        UUID spaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SpaceMembership membership = new SpaceMembership(spaceId, userId, role);

        assertEquals(spaceId, membership.getId().getSpaceId());
        assertEquals(userId, membership.getId().getUserId());
        assertEquals(role, membership.getRole());
        assertNull(membership.getJoinedAt());
    }

    @Test
    void shouldRejectMissingIdentifiersAndRole() {
        UUID id = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> new SpaceMembership(null, id, SpaceRole.OWNER));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpaceMembership(id, null, SpaceRole.OWNER));
        assertThrows(IllegalArgumentException.class, () -> new SpaceMembership(id, id, null));
    }

    @Test
    void shouldCompareKeysUsingBothIdentifiers() {
        UUID spaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SpaceMembershipId key = new SpaceMembershipId(spaceId, userId);
        SpaceMembershipId sameKey = new SpaceMembershipId(spaceId, userId);

        assertEquals(key, key);
        assertEquals(key, sameKey);
        assertEquals(sameKey, key);
        assertEquals(key.hashCode(), sameKey.hashCode());
        assertNotEquals(key, new SpaceMembershipId(UUID.randomUUID(), userId));
        assertNotEquals(key, new SpaceMembershipId(spaceId, UUID.randomUUID()));
        assertNotEquals(key, null);
        assertNotEquals(key, new Object());
    }

    @Test
    void shouldRejectMissingKeyComponents() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new SpaceMembershipId(null, id));
        assertThrows(IllegalArgumentException.class, () -> new SpaceMembershipId(id, null));
    }
}
