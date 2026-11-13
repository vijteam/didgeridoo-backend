package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.NoSuchElementException;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskReadingTest {
    @Autowired private TaskReadingService reading;
    @Autowired private TaskCreationService creation;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TaskSpaceAccess access;
    private UUID space;
    private UUID actor;
    private UUID task;

    @BeforeEach
    void setup() {
        space = UUID.randomUUID();
        actor = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id, username, display_name, email, password_hash) VALUES (?, ?, 'Test', ?, 'hash')",
                actor,
                actor.toString().substring(0, 20),
                actor + "@test.example");
        initializer.initialize(space);
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.OWNER);
        task =
                creation.create(
                        space, actor, new CreateTask("First", null, null, null, null, null, false));
    }

    @ParameterizedTest
    @EnumSource(TaskSpaceAccess.Role.class)
    void allMembersIncludingGuestsCanRead(TaskSpaceAccess.Role role) {
        when(access.roleOf(space, actor)).thenReturn(role);
        assertEquals(task, reading.get(space, actor, task).id());
        assertEquals("First", reading.get(space, actor, task).title());
        assertEquals(actor, reading.get(space, actor, task).creatorId());
        assertEquals(1, reading.list(space, actor, 0, 20).getTotalElements());
    }

    @Test
    void outsiderCannotReadEvenEmptyList() {
        when(access.roleOf(space, actor)).thenReturn(null);
        assertThrows(SecurityException.class, () -> reading.get(space, actor, task));
        assertThrows(SecurityException.class, () -> reading.list(space, actor, 0, 20));
        UUID emptySpace = UUID.randomUUID();
        initializer.initialize(emptySpace);
        when(access.roleOf(emptySpace, actor)).thenReturn(null);
        assertThrows(SecurityException.class, () -> reading.list(emptySpace, actor, 0, 20));
    }

    @Test
    void doesNotReadTaskThroughAnotherSpace() {
        UUID other = UUID.randomUUID();
        when(access.roleOf(other, actor)).thenReturn(TaskSpaceAccess.Role.MEMBER);
        assertThrows(NoSuchElementException.class, () -> reading.get(other, actor, task));
        assertTrue(reading.list(other, actor, 0, 20).isEmpty());
        assertThrows(
                NoSuchElementException.class, () -> reading.get(other, actor, UUID.randomUUID()));
    }

    @Test
    void checksCurrentMembershipOnEveryCall() {
        reading.get(space, actor, task);
        when(access.roleOf(space, actor)).thenReturn(null);
        assertThrows(SecurityException.class, () -> reading.get(space, actor, task));
    }

    @Test
    void pagesInStableCreationOrder() {
        UUID second =
                creation.create(
                        space,
                        actor,
                        new CreateTask("Second", null, null, null, null, null, false));
        assertEquals(task, reading.list(space, actor, 0, 1).getContent().get(0).id());
        assertEquals(second, reading.list(space, actor, 1, 1).getContent().get(0).id());
        assertEquals(2, reading.list(space, actor, 0, 1).getTotalElements());
    }

    @Test
    void rejectsInvalidPaginationAndIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> reading.list(space, actor, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> reading.list(space, actor, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> reading.list(space, actor, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> reading.get(space, actor, null));
        assertThrows(IllegalArgumentException.class, () -> reading.get(null, actor, task));
        assertThrows(IllegalArgumentException.class, () -> reading.list(space, null, 0, 20));
    }
}
