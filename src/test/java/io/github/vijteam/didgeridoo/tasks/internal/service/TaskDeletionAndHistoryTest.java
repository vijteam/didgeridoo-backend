package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.NoSuchElementException;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskDeletionAndHistoryTest {
    @Autowired private TaskDeletionService deletion;
    @Autowired private TaskHistoryService history;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transaction;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskReadingService reading;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TaskSpaceAccess access;
    private UUID space;
    private UUID creator;
    private UUID other;
    private UUID task;

    @BeforeEach
    void setup() {
        space = UUID.randomUUID();
        creator = user();
        other = user();
        initializer.initialize(space);
        when(access.roleOf(space, creator)).thenReturn(TaskSpaceAccess.Role.MEMBER);
        when(access.roleOf(space, other)).thenReturn(TaskSpaceAccess.Role.MEMBER);
        task =
                creation.create(
                        space,
                        creator,
                        new CreateTask("Before", "Details", null, null, null, creator, false));
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,username,display_name,email,password_hash) VALUES (?,?,'Test',?,'hash')",
                id,
                id.toString().substring(0, 20),
                id + "@test.example");
        return id;
    }

    @Test
    void creatorDeletesConfirmedTaskWithHistoryButKeepsStatuses() {
        deletion.delete(space, creator, task, true);
        assertEquals(
                0,
                jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id=?", Integer.class, task));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id=?", Integer.class, task));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_statuses WHERE space_id=?",
                        Integer.class,
                        space));
    }

    @Test
    void ownerCanDeleteOthersTask() {
        when(access.roleOf(space, other)).thenReturn(TaskSpaceAccess.Role.OWNER);
        deletion.delete(space, other, task, true);
        assertThrows(NoSuchElementException.class, () -> reading.get(space, creator, task));
    }

    @Test
    void requiresConfirmation() {
        assertThrows(
                IllegalArgumentException.class, () -> deletion.delete(space, creator, task, false));
        assertEquals(task, reading.get(space, creator, task).id());
    }

    @Test
    void assigneeGuestAndFormerCreatorCannotDelete() {
        jdbc.update(
                "UPDATE tasks SET assignee_user_id=?, assignee_can_edit=true WHERE id=?",
                other,
                task);
        assertThrows(SecurityException.class, () -> deletion.delete(space, other, task, true));
        when(access.roleOf(space, creator)).thenReturn(TaskSpaceAccess.Role.GUEST);
        assertThrows(SecurityException.class, () -> deletion.delete(space, creator, task, true));
        when(access.roleOf(space, creator)).thenReturn(null);
        assertThrows(SecurityException.class, () -> deletion.delete(space, creator, task, true));
    }

    @Test
    void deletionAndHistoryRollbackWithWorkflow() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                tx -> {
                                    deletion.delete(space, creator, task, true);
                                    throw new IllegalStateException("Workflow failed");
                                }));
        assertEquals(task, reading.get(space, creator, task).id());
        assertEquals(1, history.list(space, creator, task, 0, 20).getTotalElements());
    }

    @Test
    void guestsCanReadImmutableHistoryWithActorAndNullOldValue() {
        when(access.roleOf(space, other)).thenReturn(TaskSpaceAccess.Role.GUEST);
        var change = history.list(space, other, task, 0, 20).getContent().get(0);
        assertEquals("CREATED", change.kind());
        assertEquals(creator, change.actorId());
        assertEquals("Before", change.changedFields().get("title").get("new"));
        assertEquals(null, change.changedFields().get("title").get("old"));
        assertThrows(UnsupportedOperationException.class, () -> change.changedFields().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> change.changedFields().get("title").put("new", "Tampered"));
    }

    @Test
    void historyIsPaginatedAndScopedToTask() {
        UUID finalStatus =
                jdbc.queryForObject(
                        "SELECT id FROM task_statuses WHERE space_id=? AND kind='FINAL'",
                        UUID.class,
                        space);
        jdbc.update(
                "INSERT INTO task_changes(task_id,actor_user_id,kind,changed_fields,created_at) VALUES (?,?,'UPDATED','{}',now() + interval '1 second')",
                task,
                creator);
        UUID another =
                creation.create(
                        space,
                        creator,
                        new CreateTask("Another", null, null, null, finalStatus, null, false));
        assertEquals(
                "CREATED", history.list(space, creator, task, 0, 1).getContent().get(0).kind());
        assertEquals(
                "UPDATED", history.list(space, creator, task, 1, 1).getContent().get(0).kind());
        assertEquals(2, history.list(space, creator, task, 0, 1).getTotalElements());
        assertEquals(1, history.list(space, creator, another, 0, 20).getTotalElements());
    }

    @Test
    void deniesOutsiderAndCrossSpaceRequests() {
        when(access.roleOf(space, other)).thenReturn(null);
        assertThrows(SecurityException.class, () -> history.list(space, other, task, 0, 20));
        UUID wrong = UUID.randomUUID();
        when(access.roleOf(wrong, creator)).thenReturn(TaskSpaceAccess.Role.OWNER);
        assertThrows(NoSuchElementException.class, () -> history.list(wrong, creator, task, 0, 20));
        assertThrows(
                NoSuchElementException.class, () -> deletion.delete(wrong, creator, task, true));
    }

    @Test
    void historyRemainsReadableAfterActorAccountDeletion() {
        jdbc.update(
                "UPDATE tasks SET assignee_user_id=null, assignment_source=null WHERE id=?", task);
        jdbc.update("DELETE FROM users WHERE id=?", creator);
        when(access.roleOf(space, other)).thenReturn(TaskSpaceAccess.Role.GUEST);
        assertEquals(null, history.list(space, other, task, 0, 20).getContent().get(0).actorId());
    }

    @Test
    void validatesHistoryPagination() {
        assertThrows(
                IllegalArgumentException.class, () -> history.list(space, creator, task, -1, 20));
        assertThrows(
                IllegalArgumentException.class, () -> history.list(space, creator, task, 0, 101));
        assertThrows(
                IllegalArgumentException.class, () -> history.list(space, creator, null, 0, 20));
    }
}
