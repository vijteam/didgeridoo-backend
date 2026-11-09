package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskCreationTest {
    @Autowired private TaskCreationService service;
    @Autowired private StatusInitializer initializer;
    @Autowired private TaskRepository tasks;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;
    @MockitoBean private TaskSpaceAccess access;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository changes;

    private UUID space;
    private UUID actor;

    @BeforeEach
    void setup() {
        space = UUID.randomUUID();
        actor = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id, username, display_name, email, password_hash) VALUES (?, ?, 'Test', ?, 'hash')",
                actor,
                actor.toString().replace("-", "").substring(0, 20),
                actor + "@test.example");
        initializer.initialize(space);
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.MEMBER);
    }

    private CreateTask command(UUID assignee) {
        return new CreateTask("Task", null, null, null, null, assignee, false);
    }

    @Test
    void persistsDefaultsAndCreatedSnapshot() {
        UUID id = service.create(space, actor, command(null));
        var task = tasks.findByIdAndSpaceId(id, space).orElseThrow();
        assertEquals("MEDIUM", task.getPriority().name());
        assertEquals(
                jdbc.queryForObject(
                        "SELECT id FROM task_statuses WHERE space_id=? AND kind='INITIAL'",
                        UUID.class,
                        space),
                task.getStatusId());
        assertEquals(
                actor,
                jdbc.queryForObject(
                        "SELECT actor_user_id FROM task_changes WHERE task_id=?", UUID.class, id));
        assertEquals(
                "null",
                jdbc.queryForObject(
                        "SELECT (changed_fields->'title'->'old')::text FROM task_changes WHERE task_id=?",
                        String.class,
                        id));
        assertNull(task.getAssigneeId());
        assertFalse(task.isAssigneeCanEdit());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id = ? AND kind = 'CREATED'",
                        Integer.class,
                        id));
        assertEquals(
                "Task",
                jdbc.queryForObject(
                        "SELECT changed_fields->'title'->>'new' FROM task_changes WHERE task_id = ?",
                        String.class,
                        id));
    }

    @Test
    void memberCanAssignSelfButNotOthers() {
        UUID id = service.create(space, actor, command(actor));
        assertEquals(actor, tasks.findById(id).orElseThrow().getAssigneeId());
        assertThrows(
                SecurityException.class,
                () -> service.create(space, actor, command(UUID.randomUUID())));
    }

    @Test
    void guestAndOutsiderCannotCreate() {
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.GUEST);
        assertThrows(SecurityException.class, () -> service.create(space, actor, command(null)));
        when(access.roleOf(space, actor)).thenReturn(null);
        assertThrows(SecurityException.class, () -> service.create(space, actor, command(null)));
        assertTrue(tasks.findAllBySpaceIdOrderByCreatedAtAscIdAsc(space).isEmpty());
    }

    @Test
    void ownerCanAssignAnotherMemberWithExplicitFields() {
        UUID assignee = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id, username, display_name, email, password_hash) VALUES (?, ?, 'Member', ?, 'hash')",
                assignee,
                assignee.toString().substring(0, 20),
                assignee + "@test.example");
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.OWNER);
        when(access.roleOf(space, assignee)).thenReturn(TaskSpaceAccess.Role.MEMBER);
        UUID finalStatus =
                jdbc.queryForObject(
                        "SELECT id FROM task_statuses WHERE space_id=? AND kind='FINAL'",
                        UUID.class,
                        space);
        UUID id =
                service.create(
                        space,
                        actor,
                        new CreateTask(
                                "Assigned",
                                "Details",
                                null,
                                io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority.HIGH,
                                finalStatus,
                                assignee,
                                true));
        var task = tasks.findById(id).orElseThrow();
        assertEquals(assignee, task.getAssigneeId());
        assertEquals(finalStatus, task.getStatusId());
        assertTrue(task.isAssigneeCanEdit());
        assertEquals("DIRECT", task.getAssignmentSource().name());
        assertEquals("HIGH", task.getPriority().name());
    }

    @Test
    void ownerCannotAssignGuest() {
        UUID guest = UUID.randomUUID();
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.OWNER);
        when(access.roleOf(space, guest)).thenReturn(TaskSpaceAccess.Role.GUEST);
        assertThrows(
                TaskValidationException.class, () -> service.create(space, actor, command(guest)));
    }

    @Test
    void rejectsStatusFromDifferentSpace() {
        UUID other = UUID.randomUUID();
        initializer.initialize(other);
        UUID status =
                jdbc.queryForObject(
                        "SELECT id FROM task_statuses WHERE space_id = ? AND kind = 'INITIAL'",
                        UUID.class,
                        other);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.create(
                                space,
                                actor,
                                new CreateTask("Task", null, null, null, status, null, false)));
    }

    @Test
    void historyFailureRollsBackInsertedTask() {
        doThrow(new IllegalStateException("History unavailable"))
                .when(changes)
                .saveAndFlush(org.mockito.ArgumentMatchers.any());
        assertThrows(
                IllegalStateException.class, () -> service.create(space, actor, command(null)));
        assertTrue(tasks.findAllBySpaceIdOrderByCreatedAtAscIdAsc(space).isEmpty());
    }

    @Test
    void taskAndHistoryJoinWorkflowRollback() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                tx -> {
                                    service.create(space, actor, command(null));
                                    throw new IllegalStateException("Workflow failed");
                                }));
        assertTrue(tasks.findAllBySpaceIdOrderByCreatedAtAscIdAsc(space).isEmpty());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes c JOIN tasks t ON t.id=c.task_id WHERE t.space_id=?",
                        Integer.class,
                        space));
    }
}
