package io.github.vijteam.didgeridoo.tasks.internal.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.spaces.api.SpaceAccess;
import io.github.vijteam.didgeridoo.spaces.api.SpaceRole;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskSpaceAccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

class TaskSpaceAccessAdapterTest {
    private final SpaceAccess spaces = mock(SpaceAccess.class);
    private final TaskSpaceAccessAdapter adapter = new TaskSpaceAccessAdapter(spaces);
    private final UUID space = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void mapsEveryPublicRole(SpaceRole role) {
        when(spaces.findRole(space, actor)).thenReturn(Optional.of(role));
        assertEquals(TaskSpaceAccess.Role.valueOf(role.name()), adapter.roleOf(space, actor));
    }

    @Test
    void readsCurrentMembershipWithoutCaching() {
        when(spaces.findRole(space, actor))
                .thenReturn(Optional.of(SpaceRole.MEMBER), Optional.empty());
        assertEquals(TaskSpaceAccess.Role.MEMBER, adapter.roleOf(space, actor));
        assertNull(adapter.roleOf(space, actor));
    }

    @Test
    void doesNotTurnProviderFailureIntoMissingMembership() {
        when(spaces.findRole(space, actor)).thenThrow(new IllegalStateException("Provider failed"));
        assertThrows(IllegalStateException.class, () -> adapter.roleOf(space, actor));
    }
}
