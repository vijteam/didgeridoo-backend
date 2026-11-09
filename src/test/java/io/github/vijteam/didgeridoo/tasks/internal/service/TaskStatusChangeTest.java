package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.NoSuchElementException;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskStatusChangeTest {
    @Autowired private TaskStatusChangeService statusChange;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskReadingService reading;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private TaskSpaceAccess access;
    @MockitoSpyBean private TaskChangeRepository changes;
    private UUID space;
    private UUID creator;
    private UUID other;
    private UUID task;
    private UUID finalStatus;

    @BeforeEach
    void setup() {
        space = UUID.randomUUID();
        creator = user();
        other = user();
        initializer.initialize(space);
        finalStatus =
                jdbc.queryForObject(
                        "SELECT id FROM task_statuses WHERE space_id=? AND kind='FINAL'",
                        UUID.class,
                        space);
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
    void creatorChangesStatusAndWritesHistoryWithoutChangingAssignment() {
        var before = reading.get(space, creator, task);
        var after = statusChange.change(space, creator, task, finalStatus);
        assertEquals(finalStatus, after.statusId());
        assertEquals(before.assigneeId(), after.assigneeId());
        assertEquals(before.assignmentSource(), after.assignmentSource());
        assertEquals(before.title(), after.title());
        assertEquals(
                before.statusId().toString(),
                jdbc.queryForObject(
                        "SELECT changed_fields->'statusId'->>'old' FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        String.class,
                        task));
        assertEquals(
                finalStatus.toString(),
                jdbc.queryForObject(
                        "SELECT changed_fields->'statusId'->>'new' FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        String.class,
                        task));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes c, LATERAL jsonb_object_keys(c.changed_fields) WHERE task_id=? AND kind='UPDATED'",
                        Integer.class,
                        task));
    }

    @Test
    void ownerCanChangeAssignedTaskOfAnotherCreator() {
        when(access.roleOf(space, other)).thenReturn(TaskSpaceAccess.Role.OWNER);
        assertEquals(finalStatus, statusChange.change(space, other, task, finalStatus).statusId());
    }

    @Test
    void assigneeCanChangeStatusWithoutContentEditingPermission() {
        jdbc.update("UPDATE tasks SET assignee_user_id=? WHERE id=?", other, task);
        assertEquals(finalStatus, statusChange.change(space, other, task, finalStatus).statusId());
    }

    @Test
    void anyMemberCanChangeFreeTaskButNotSomeoneElsesAssignedTask() {
        assertThrows(
                SecurityException.class,
                () -> statusChange.change(space, other, task, finalStatus));
        jdbc.update(
                "UPDATE tasks SET assignee_user_id=null, assignment_source=null WHERE id=?", task);
        assertEquals(finalStatus, statusChange.change(space, other, task, finalStatus).statusId());
    }

    @Test
    void guestAndFormerCreatorCannotChangeStatus() {
        when(access.roleOf(space, creator)).thenReturn(TaskSpaceAccess.Role.GUEST);
        assertThrows(
                SecurityException.class,
                () -> statusChange.change(space, creator, task, finalStatus));
        when(access.roleOf(space, creator)).thenReturn(null);
        assertThrows(
                SecurityException.class,
                () -> statusChange.change(space, creator, task, finalStatus));
    }

    @Test
    void rejectsStatusFromAnotherSpaceAndUnknownStatus() {
        UUID anotherSpace = UUID.randomUUID();
        initializer.initialize(anotherSpace);
        UUID foreignStatus =
                jdbc.queryForObject(
                        "SELECT id FROM task_statuses WHERE space_id=? AND kind='FINAL'",
                        UUID.class,
                        anotherSpace);
        assertThrows(
                IllegalArgumentException.class,
                () -> statusChange.change(space, creator, task, foreignStatus));
        assertThrows(
                IllegalArgumentException.class,
                () -> statusChange.change(space, creator, task, UUID.randomUUID()));
        assertThrows(
                IllegalArgumentException.class,
                () -> statusChange.change(space, creator, task, null));
        when(access.roleOf(anotherSpace, creator)).thenReturn(TaskSpaceAccess.Role.OWNER);
        assertThrows(
                NoSuchElementException.class,
                () -> statusChange.change(anotherSpace, creator, task, foreignStatus));
    }

    @Test
    void sameStatusDoesNotChangeTimestampOrAddHistory() {
        var before = reading.get(space, creator, task);
        var after = statusChange.change(space, creator, task, before.statusId());
        assertEquals(before.updatedAt(), after.updatedAt());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        Integer.class,
                        task));
    }

    @Test
    void historyFailureRollsBackStatus() {
        var before = reading.get(space, creator, task);
        doThrow(new IllegalStateException("History failed")).when(changes).saveAndFlush(any());
        assertThrows(
                IllegalStateException.class,
                () -> statusChange.change(space, creator, task, finalStatus));
        assertEquals(before.statusId(), reading.get(space, creator, task).statusId());
    }

    @Test
    void canReopenFinalTask() {
        UUID initial = reading.get(space, creator, task).statusId();
        statusChange.change(space, creator, task, finalStatus);
        assertEquals(initial, statusChange.change(space, creator, task, initial).statusId());
    }
}
