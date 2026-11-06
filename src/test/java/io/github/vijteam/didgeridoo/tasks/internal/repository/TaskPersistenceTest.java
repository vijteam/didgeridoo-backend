package io.github.vijteam.didgeridoo.tasks.internal.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.internal.model.AssignmentSource;
import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.model.Task;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskStatus;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TaskPersistenceTest {
    @Autowired private TaskRepository tasks;
    @Autowired private TaskStatusRepository statuses;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private UUID spaceId;
    private UUID userId;
    private TaskStatus initial;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, username, display_name, email, password_hash) VALUES (?, ?, 'Test', ?, 'hash')",
                userId,
                "u" + userId.toString().replace("-", "").substring(0, 20),
                userId + "@example.com");
        initial = statuses.saveAndFlush(new TaskStatus(spaceId, "New", StatusKind.INITIAL, 0));
    }

    private Task newTask() {
        return new Task(spaceId, userId, initial.getId(), "Task");
    }

    @Test
    void shouldRoundTripTaskAndStatus() {
        Task task = newTask();
        Instant deadline = Instant.parse("2026-11-08T12:00:00Z");
        task.updateDetails("🚀".repeat(255), "Details", deadline, TaskPriority.URGENT, true);
        task.assign(userId, AssignmentSource.DIRECT);
        tasks.saveAndFlush(task);
        UUID id = task.getId();
        assertNotNull(id);
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
        entityManager.clear();
        Task loaded = tasks.findByIdAndSpaceId(id, spaceId).orElseThrow();
        assertEquals("🚀".repeat(255), loaded.getTitle());
        assertEquals("Details", loaded.getDescription());
        assertEquals(deadline, loaded.getDeadline());
        assertEquals(TaskPriority.URGENT, loaded.getPriority());
        assertTrue(loaded.isAssigneeCanEdit());
        assertEquals(userId, loaded.getCreatorId());
        assertEquals(userId, loaded.getAssigneeId());
        assertEquals(AssignmentSource.DIRECT, loaded.getAssignmentSource());
        assertEquals(initial.getId(), loaded.getStatusId());
        assertTrue(tasks.findByIdAndSpaceId(id, UUID.randomUUID()).isEmpty());
        assertTrue(tasks.findAllBySpaceIdOrderByCreatedAtAscIdAsc(UUID.randomUUID()).isEmpty());
        assertEquals(id, tasks.findAllBySpaceIdOrderByCreatedAtAscIdAsc(spaceId).get(0).getId());
        TaskStatus status =
                statuses.findFirstBySpaceIdAndKindOrderByPositionAsc(spaceId, StatusKind.INITIAL)
                        .orElseThrow();
        assertEquals("New", status.getName());
        assertNotNull(status.getCreatedAt());
    }

    @Test
    void shouldPersistChangesAndClearAssignment() {
        Task task = tasks.saveAndFlush(newTask());
        TaskStatus done =
                statuses.saveAndFlush(new TaskStatus(spaceId, "Done", StatusKind.FINAL, 1));
        task.assign(userId, AssignmentSource.SELF_CLAIM);
        tasks.flush();
        task.unassign();
        task.changeStatus(done.getId());
        task.updateDetails("Updated", null, null, TaskPriority.LOW, false);
        tasks.flush();
        entityManager.clear();
        Task loaded = tasks.findById(task.getId()).orElseThrow();
        assertEquals("Updated", loaded.getTitle());
        assertEquals(done.getId(), loaded.getStatusId());
        assertNull(loaded.getAssigneeId());
        assertNull(loaded.getAssignmentSource());
        assertFalse(loaded.getUpdatedAt().isBefore(loaded.getCreatedAt()));
        assertEquals(2, statuses.findAllBySpaceIdOrderByPositionAsc(spaceId).size());
    }

    @Test
    void shouldRejectStatusFromAnotherSpace() {
        TaskStatus foreign =
                statuses.saveAndFlush(
                        new TaskStatus(UUID.randomUUID(), "Other", StatusKind.INITIAL, 0));
        Task task = new Task(spaceId, userId, foreign.getId(), "Invalid");
        assertThrows(DataIntegrityViolationException.class, () -> tasks.saveAndFlush(task));
    }

    @Test
    void shouldRejectNonexistentAssignee() {
        Task task = newTask();
        task.assign(UUID.randomUUID(), AssignmentSource.DIRECT);
        assertThrows(DataIntegrityViolationException.class, () -> tasks.saveAndFlush(task));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "title = ' '",
                "priority = 'INVALID'",
                "assignment_source = 'DIRECT'",
                "status_id = NULL"
            })
    void shouldEnforceTaskConstraintsForDirectSql(String change) {
        Task task = tasks.saveAndFlush(newTask());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE tasks SET " + change + " WHERE id = ?", task.getId()));
    }

    @Test
    void shouldRejectSecondInitialStatusEvenAtAnotherPosition() {
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        statuses.saveAndFlush(
                                new TaskStatus(spaceId, "Duplicate", StatusKind.INITIAL, 2)));
    }

    @Test
    void shouldRejectDuplicateStatusPosition() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> statuses.saveAndFlush(new TaskStatus(spaceId, "Done", StatusKind.FINAL, 0)));
    }

    @Test
    void shouldPreserveTaskAndHistoryWhenUnassignedCreatorIsDeleted() {
        Task task = tasks.saveAndFlush(newTask());
        jdbc.update(
                "INSERT INTO task_changes (task_id, actor_user_id, kind, changed_fields) VALUES (?, ?, 'CREATED', '{}'::jsonb)",
                task.getId(),
                userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        entityManager.clear();
        assertNull(tasks.findById(task.getId()).orElseThrow().getCreatorId());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id = ? AND actor_user_id IS NULL",
                        Integer.class,
                        task.getId()));
    }

    @Test
    void shouldPersistWithoutCreatingSpacesModuleTables() {
        tasks.saveAndFlush(newTask());
        assertNull(jdbc.queryForObject("SELECT to_regclass('public.spaces')::text", String.class));
        assertNull(
                jdbc.queryForObject(
                        "SELECT to_regclass('public.space_memberships')::text", String.class));
    }

    @Test
    void shouldDeleteTaskWithHistoryAndKeepStatuses() {
        Task task = newTask();
        task.assign(userId, AssignmentSource.DIRECT);
        tasks.saveAndFlush(task);
        jdbc.update(
                "INSERT INTO task_changes (task_id, kind, changed_fields) VALUES (?, 'CREATED', '{}'::jsonb)",
                task.getId());
        tasks.delete(task);
        tasks.flush();
        entityManager.clear();
        assertTrue(tasks.findById(task.getId()).isEmpty());
        assertEquals(1, statuses.findAllBySpaceIdOrderByPositionAsc(spaceId).size());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id = ?",
                        Integer.class,
                        task.getId()));
    }
}
