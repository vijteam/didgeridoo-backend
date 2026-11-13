package io.github.vijteam.didgeridoo.tasks.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

class TaskTest {
    private final UUID spaceId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID statusId = UUID.randomUUID();

    private Task task() {
        return new Task(spaceId, creatorId, statusId, "Первая задача");
    }

    @Test
    void shouldCreateUnassignedTaskWithDefaults() {
        Task task = task();
        assertEquals(TaskPriority.MEDIUM, task.getPriority());
        assertFalse(task.isAssigneeCanEdit());
        assertNull(task.getAssigneeId());
        assertNull(task.getAssignmentSource());
        assertNull(task.getDeadline());
        assertNull(task.getDescription());
        assertEquals(statusId, task.getStatusId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void shouldRejectBlankTitle(String title) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Task(spaceId, creatorId, statusId, title));
    }

    @Test
    void shouldValidateTitleByUnicodeCodePoints() {
        String title = "🚀".repeat(255);
        Task task = new Task(spaceId, creatorId, statusId, title);
        assertEquals(title, task.getTitle());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Task(spaceId, creatorId, statusId, "🚀".repeat(256)));
    }

    @Test
    void shouldRequireSpaceCreatorAndStatusOnCreation() {
        assertThrows(
                IllegalArgumentException.class, () -> new Task(null, creatorId, statusId, "Task"));
        assertThrows(
                IllegalArgumentException.class, () -> new Task(spaceId, null, statusId, "Task"));
        assertThrows(
                IllegalArgumentException.class, () -> new Task(spaceId, creatorId, null, "Task"));
    }

    @Test
    void shouldUpdateDetailsWithoutChangingOwnershipOrAssignment() {
        Task task = task();
        UUID assignee = UUID.randomUUID();
        task.assign(assignee, AssignmentSource.DIRECT);
        Instant deadline = Instant.parse("2026-11-08T12:00:00Z");
        task.updateDetails("Новое название", "Описание", deadline, TaskPriority.HIGH, true);
        assertEquals("Новое название", task.getTitle());
        assertEquals("Описание", task.getDescription());
        assertEquals(deadline, task.getDeadline());
        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertTrue(task.isAssigneeCanEdit());
        assertEquals(spaceId, task.getSpaceId());
        assertEquals(creatorId, task.getCreatorId());
        assertEquals(statusId, task.getStatusId());
        assertEquals(assignee, task.getAssigneeId());
        assertEquals(AssignmentSource.DIRECT, task.getAssignmentSource());
    }

    @Test
    void shouldRejectInvalidUpdateWithoutPartialChanges() {
        Task task = task();
        assertThrows(
                IllegalArgumentException.class,
                () -> task.updateDetails("Changed", "Description", Instant.now(), null, true));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        task.updateDetails(
                                " ", "Description", Instant.now(), TaskPriority.URGENT, true));
        assertEquals("Первая задача", task.getTitle());
        assertNull(task.getDescription());
        assertNull(task.getDeadline());
        assertEquals(TaskPriority.MEDIUM, task.getPriority());
        assertFalse(task.isAssigneeCanEdit());
    }

    @Test
    void shouldAllowClearingOptionalDetails() {
        Task task = task();
        task.updateDetails("Task", "Description", Instant.now(), TaskPriority.LOW, true);
        task.updateDetails("Task", null, null, TaskPriority.MEDIUM, false);
        assertNull(task.getDescription());
        assertNull(task.getDeadline());
        assertFalse(task.isAssigneeCanEdit());
    }

    @Test
    void shouldChangeStatusWithoutChangingAssignment() {
        Task task = task();
        UUID assignee = UUID.randomUUID();
        UUID nextStatus = UUID.randomUUID();
        task.assign(assignee, AssignmentSource.SELF_CLAIM);
        task.changeStatus(nextStatus);
        assertEquals(nextStatus, task.getStatusId());
        assertEquals(assignee, task.getAssigneeId());
        assertEquals(AssignmentSource.SELF_CLAIM, task.getAssignmentSource());
        assertThrows(IllegalArgumentException.class, () -> task.changeStatus(null));
        assertEquals(nextStatus, task.getStatusId());
    }

    @Test
    void shouldKeepAssigneeAndSourceConsistent() {
        Task task = task();
        UUID assignee = UUID.randomUUID();
        task.assign(assignee, AssignmentSource.SELF_CLAIM);
        assertThrows(
                IllegalArgumentException.class, () -> task.assign(null, AssignmentSource.DIRECT));
        assertThrows(IllegalArgumentException.class, () -> task.assign(UUID.randomUUID(), null));
        assertEquals(assignee, task.getAssigneeId());
        assertEquals(AssignmentSource.SELF_CLAIM, task.getAssignmentSource());
        task.assign(assignee, AssignmentSource.DIRECT);
        assertEquals(AssignmentSource.DIRECT, task.getAssignmentSource());
        task.unassign();
        assertNull(task.getAssigneeId());
        assertNull(task.getAssignmentSource());
    }
}
