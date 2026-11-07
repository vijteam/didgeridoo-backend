package io.github.vijteam.didgeridoo.spaces.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.spaces.api.SpaceAccessDeniedException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceNotFoundException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembershipId;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceMembershipRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

class SpaceAccessServiceTest {
    private final SpaceMembershipRepository memberships = mock(SpaceMembershipRepository.class);
    private final SpaceAccessService service = new SpaceAccessService(memberships);
    private final UUID spaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final SpaceMembershipId key = new SpaceMembershipId(spaceId, userId);

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void shouldReadRoleAndAllowEveryMember(SpaceRole role) {
        when(memberships.findById(key))
                .thenReturn(Optional.of(new SpaceMembership(spaceId, userId, role)));

        assertEquals(Optional.of(role), service.findRole(spaceId, userId));
        service.requireMember(spaceId, userId);
        assertEquals(role != SpaceRole.GUEST, service.isAssignable(spaceId, userId));
        if (role == SpaceRole.OWNER) {
            service.requireOwner(spaceId, userId);
        } else {
            assertThrows(
                    SpaceAccessDeniedException.class, () -> service.requireOwner(spaceId, userId));
        }
    }

    @Test
    void shouldHideSpaceFromNonmember() {
        when(memberships.findById(key)).thenReturn(Optional.empty());

        assertTrue(service.findRole(spaceId, userId).isEmpty());
        assertFalse(service.isAssignable(spaceId, userId));
        assertThrows(SpaceNotFoundException.class, () -> service.requireMember(spaceId, userId));
        assertThrows(SpaceNotFoundException.class, () -> service.requireOwner(spaceId, userId));
    }

    @Test
    void shouldUseCurrentMembershipOnEachCall() {
        when(memberships.findById(key))
                .thenReturn(
                        Optional.of(new SpaceMembership(spaceId, userId, SpaceRole.OWNER)),
                        Optional.of(new SpaceMembership(spaceId, userId, SpaceRole.GUEST)),
                        Optional.empty());

        service.requireOwner(spaceId, userId);
        assertFalse(service.isAssignable(spaceId, userId));
        assertThrows(SpaceNotFoundException.class, () -> service.requireMember(spaceId, userId));
    }

    @Test
    void shouldRejectMissingIdentifiersBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> service.findRole(null, userId));
        assertThrows(IllegalArgumentException.class, () -> service.requireMember(spaceId, null));
        assertThrows(IllegalArgumentException.class, () -> service.requireOwner(null, userId));
        assertThrows(IllegalArgumentException.class, () -> service.isAssignable(spaceId, null));
        verifyNoInteractions(memberships);
    }
}
