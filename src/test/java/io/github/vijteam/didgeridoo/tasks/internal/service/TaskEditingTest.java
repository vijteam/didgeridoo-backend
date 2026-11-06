package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;
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
class TaskEditingTest {
    @Autowired private TaskEditingService editing;
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

    private EditTask command(Boolean canEdit) {
        return new EditTask("After", null, null, TaskPriority.HIGH, canEdit);
    }

    @Test
    void creatorEditsAndRecordsOnlyChangedFields() {
        var before = reading.get(space, creator, task);
        var result = editing.edit(space, creator, task, command(true));
        assertEquals("After", result.title());
        assertEquals(before.assigneeId(), result.assigneeId());
        assertEquals(before.statusId(), result.statusId());
        assertEquals(
                "Before",
                jdbc.queryForObject(
                        "SELECT changed_fields->'title'->>'old' FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        String.class,
                        task));
        assertEquals(
                "After",
                jdbc.queryForObject(
                        "SELECT changed_fields->'title'->>'new' FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        String.class,
                        task));
        assertEquals(
                "null",
                jdbc.queryForObject(
                        "SELECT (changed_fields->'description'->'new')::text FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        String.class,
                        task));
        assertEquals(
                false,
                jdbc.queryForObject(
                        "SELECT jsonb_exists(changed_fields, 'statusId') FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        Boolean.class,
                        task));
    }

    @Test
    void ownerEditsOthersTask() {
        when(access.roleOf(space, other)).thenReturn(TaskSpaceAccess.Role.OWNER);
        assertEquals("After", editing.edit(space, other, task, command(true)).title());
    }

    @Test
    void unrelatedMemberCannotEdit() {
        assertThrows(
                SecurityException.class, () -> editing.edit(space, other, task, command(null)));
    }

    @Test
    void assigneeNeedsPermissionAndCannotChangePermissionFlag() {
        jdbc.update("UPDATE tasks SET assignee_user_id=? WHERE id=?", other, task);
        assertThrows(
                SecurityException.class, () -> editing.edit(space, other, task, command(null)));
        jdbc.update("UPDATE tasks SET assignee_can_edit=true WHERE id=?", task);
        assertEquals("After", editing.edit(space, other, task, command(null)).title());
        assertThrows(
                SecurityException.class, () -> editing.edit(space, other, task, command(false)));
    }

    @Test
    void guestOrFormerCreatorCannotEdit() {
        when(access.roleOf(space, creator)).thenReturn(TaskSpaceAccess.Role.GUEST);
        assertThrows(
                SecurityException.class, () -> editing.edit(space, creator, task, command(null)));
        when(access.roleOf(space, creator)).thenReturn(null);
        assertThrows(
                SecurityException.class, () -> editing.edit(space, creator, task, command(null)));
    }

    @Test
    void hidesTasksOfOtherSpace() {
        UUID different = UUID.randomUUID();
        when(access.roleOf(different, creator)).thenReturn(TaskSpaceAccess.Role.OWNER);
        assertThrows(
                NoSuchElementException.class,
                () -> editing.edit(different, creator, task, command(null)));
    }

    @Test
    void unchangedDetailsDoNotProduceHistoryOrTimestampUpdate() {
        var before = reading.get(space, creator, task);
        var after =
                editing.edit(
                        space,
                        creator,
                        task,
                        new EditTask("Before", "Details", null, TaskPriority.MEDIUM, null));
        assertEquals(before.updatedAt(), after.updatedAt());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                        Integer.class,
                        task));
    }

    @Test
    void historyFailureRollsBackEdit() {
        doThrow(new IllegalStateException("History failed")).when(changes).saveAndFlush(any());
        assertThrows(
                IllegalStateException.class,
                () -> editing.edit(space, creator, task, command(true)));
        assertEquals("Before", reading.get(space, creator, task).title());
    }

    @Test
    void invalidTitleDoesNotChangeTask() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        editing.edit(
                                space,
                                creator,
                                task,
                                new EditTask(" ", null, null, TaskPriority.HIGH, null)));
        assertEquals("Before", reading.get(space, creator, task).title());
    }
}
