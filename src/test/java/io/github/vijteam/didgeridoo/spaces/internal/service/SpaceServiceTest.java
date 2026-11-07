package io.github.vijteam.didgeridoo.spaces.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.identity.api.UserDirectory;
import io.github.vijteam.didgeridoo.identity.api.UserSummary;
import io.github.vijteam.didgeridoo.spaces.api.SpaceAccess;
import io.github.vijteam.didgeridoo.spaces.api.SpaceNotFoundException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.spaces.api.SpaceSummary;
import io.github.vijteam.didgeridoo.spaces.internal.model.Space;
import io.github.vijteam.didgeridoo.spaces.internal.model.SpaceMembership;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceMembershipRepository;
import io.github.vijteam.didgeridoo.spaces.internal.repository.SpaceRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SpaceServiceTest {
    private final SpaceRepository spaces = mock(SpaceRepository.class);
    private final SpaceMembershipRepository memberships = mock(SpaceMembershipRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final SpaceAccess access = mock(SpaceAccess.class);
    private final SpaceService service = new SpaceService(spaces, memberships, users, access);
    private final UUID actorId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();

    @Test
    void shouldCreateSpaceAndOwnerForExistingUser() {
        when(users.findById(actorId))
                .thenReturn(Optional.of(new UserSummary(actorId, "Alice", "Alice", null)));
        when(spaces.saveAndFlush(any(Space.class)))
                .thenAnswer(
                        invocation -> {
                            Space space = invocation.getArgument(0);
                            ReflectionTestUtils.setField(space, "id", spaceId);
                            return space;
                        });

        SpaceSummary result = service.createWithOwner(actorId, " Team ");
        
        assertEquals(spaceId, result.id());
        assertEquals("Team", result.name());
        ArgumentCaptor<SpaceMembership> captured = ArgumentCaptor.forClass(SpaceMembership.class);
        verify(memberships).saveAndFlush(captured.capture());
        assertEquals(actorId, captured.getValue().getId().getUserId());
        assertEquals(spaceId, captured.getValue().getId().getSpaceId());
        assertEquals(SpaceRole.OWNER, captured.getValue().getRole());
    }

    @Test
    void shouldRejectUnknownCreatorBeforeWriting() {
        when(users.findById(actorId)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class, () -> service.createWithOwner(actorId, "Team"));
        verifyNoInteractions(spaces, memberships);
    }

    @Test
    void shouldValidateCreatorAndNameBeforeExternalCalls() {
        assertThrows(IllegalArgumentException.class, () -> service.createWithOwner(null, "Team"));
        assertThrows(IllegalArgumentException.class, () -> service.createWithOwner(actorId, " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createWithOwner(actorId, "x".repeat(151)));
        verifyNoInteractions(users, spaces, memberships, access);
    }

    @Test
    void shouldReturnSummaryOnlyAfterMembershipCheck() {
        Space space = new Space("Team");
        ReflectionTestUtils.setField(space, "id", spaceId);
        when(spaces.findById(spaceId)).thenReturn(Optional.of(space));

        assertEquals("Team", service.get(actorId, spaceId).name());
        verify(access).requireMember(spaceId, actorId);
    }

    @Test
    void shouldHideDeniedSpaceWithoutReadingIt() {
        doThrow(new SpaceNotFoundException()).when(access).requireMember(spaceId, actorId);

        assertThrows(SpaceNotFoundException.class, () -> service.get(actorId, spaceId));
        verifyNoInteractions(spaces);
    }

    @Test
    void shouldReportMissingSpaceAfterMembershipCheck() {
        when(spaces.findById(spaceId)).thenReturn(Optional.empty());

        assertThrows(SpaceNotFoundException.class, () -> service.get(actorId, spaceId));
    }

    @Test
    void shouldSearchWithFixedBoundedPaginationAndLiteralInput() {
        PageRequest request = PageRequest.of(1, 20, Sort.by("name", "id"));
        Space space = new Space("100%_Team");
        when(spaces.findByMemberUserIdAndNameContainingIgnoreCase(actorId, "%_", request))
                .thenReturn(new PageImpl<>(List.of(space), request, 21));

        var result = service.search(actorId, "%_", 1, 20);

        assertEquals("100%_Team", result.getContent().get(0).name());
        assertEquals(21, result.getTotalElements());
        verify(spaces).findByMemberUserIdAndNameContainingIgnoreCase(actorId, "%_", request);
    }

    @Test
    void shouldRejectInvalidReadArgumentsBeforeExternalCalls() {
        assertThrows(IllegalArgumentException.class, () -> service.get(null, spaceId));
        assertThrows(IllegalArgumentException.class, () -> service.get(actorId, null));
        assertThrows(IllegalArgumentException.class, () -> service.search(null, "", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.search(actorId, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.search(actorId, "", -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.search(actorId, "", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.search(actorId, "", 0, 101));
        verifyNoInteractions(spaces, memberships, users, access);
    }
}
