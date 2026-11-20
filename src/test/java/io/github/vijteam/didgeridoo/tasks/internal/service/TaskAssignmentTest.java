package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskConflictException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.AssignmentSource;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskAssignmentTest {
    @Autowired private TaskAssignmentService assignments;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskReadingService reading;
    @Autowired private TaskHistoryService history;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private TaskChangeRepository changes;
    private UUID space;
    private UUID owner;
    private UUID member;
    private UUID other;
    private UUID guest;
    private UUID task;

    @BeforeEach
    void setup() {
        space = UUID.randomUUID();
        owner = user();
        member = user();
        other = user();
        guest = user();
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?,'Assignments')", space);
        membership(owner, "OWNER");
        membership(member, "MEMBER");
        membership(other, "MEMBER");
        membership(guest, "GUEST");
        initializer.initialize(space);
        task =
                creation.create(
                        space, owner, new CreateTask("Task", null, null, null, null, null, false));
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

    private void membership(UUID actor, String role) {
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role)",
                space,
                actor,
                role);
    }

    private int updates() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM task_changes WHERE task_id=? AND kind='UPDATED'",
                Integer.class,
                task);
    }

    @Test
    void memberClaimsAndReleasesWithHistoryWithoutChangingStatus() {
        var before = reading.get(space, member, task);
        var claimed = assignments.claim(space, member, task);
        assertEquals(member, claimed.assigneeId());
        assertEquals(AssignmentSource.SELF_CLAIM, claimed.assignmentSource());
        assertEquals(before.statusId(), claimed.statusId());
        var claimEvent = history.list(space, owner, task, 0, 100).getContent().getLast();
        assertEquals(member, claimEvent.actorId());
        assertEquals("UPDATED", claimEvent.kind());
        assertEquals(
                Map.of(
                        "assigneeId", valueChange(null, member.toString()),
                        "assignmentSource", valueChange(null, "SELF_CLAIM")),
                claimEvent.changedFields());
        var released = assignments.release(space, member, task);
        assertNull(released.assigneeId());
        assertNull(released.assignmentSource());
        assertEquals(before.title(), released.title());
        assertEquals(before.statusId(), released.statusId());
        assertEquals(2, updates());
        var releaseEvent = history.list(space, owner, task, 0, 100).getContent().getLast();
        assertEquals(member, releaseEvent.actorId());
        assertEquals("UPDATED", releaseEvent.kind());
        assertEquals(
                Map.of(
                        "assigneeId", valueChange(member.toString(), null),
                        "assignmentSource", valueChange("SELF_CLAIM", null)),
                releaseEvent.changedFields());
    }

    @Test
    void ownerHasClaimAndReleasePrivileges() {
        assignments.claim(space, owner, task);
        assertEquals(owner, reading.get(space, owner, task).assigneeId());
        assignments.release(space, owner, task);
        assignments.assignDirect(space, owner, task, member);
        assertNull(assignments.release(space, owner, task).assigneeId());
    }

    @Test
    void ownerReassignmentCancelsSelfReleaseEvenForSameAssignee() {
        assignments.claim(space, member, task);
        var direct = assignments.assignDirect(space, owner, task, member);
        assertEquals(AssignmentSource.DIRECT, direct.assignmentSource());
        var event = history.list(space, owner, task, 0, 100).getContent().getLast();
        assertEquals(owner, event.actorId());
        assertEquals(
                Map.of("assignmentSource", valueChange("SELF_CLAIM", "DIRECT")),
                event.changedFields());
        assertThrows(TaskAccessException.class, () -> assignments.release(space, member, task));
        assertEquals(member, reading.get(space, member, task).assigneeId());
    }

    @Test
    void anotherMemberAndCreatorCannotReleaseSomeoneElsesClaim() {
        UUID ownTask =
                creation.create(
                        space, member, new CreateTask("Own", null, null, null, null, null, false));
        assignments.claim(space, other, ownTask);
        assertThrows(TaskAccessException.class, () -> assignments.release(space, member, ownTask));
        assertThrows(
                TaskAccessException.class,
                () -> assignments.assignDirect(space, member, ownTask, member));
    }

    @Test
    void ownerCanReplaceAssigneeIncludingAssigningOwner() {
        assignments.assignDirect(space, owner, task, member);
        var reassigned = assignments.assignDirect(space, owner, task, other);
        assertEquals(other, reassigned.assigneeId());
        assertEquals(AssignmentSource.DIRECT, reassigned.assignmentSource());
        var assignedOwner = assignments.assignDirect(space, owner, task, owner);
        assertEquals(owner, assignedOwner.assigneeId());
        assertEquals(AssignmentSource.DIRECT, assignedOwner.assignmentSource());
        assertEquals(3, updates());
    }

    @Test
    void identifiersAndDirectAssigneeAreRequired() {
        assertThrows(TaskValidationException.class, () -> assignments.claim(null, member, task));
        assertThrows(TaskValidationException.class, () -> assignments.claim(space, null, task));
        assertThrows(TaskValidationException.class, () -> assignments.claim(space, member, null));
        var error =
                assertThrows(
                        FieldValidationException.class,
                        () -> assignments.assignDirect(space, owner, task, null));
        assertEquals("assigneeId", error.getViolations().getFirst().field());
        assertEquals(FieldErrorCode.REQUIRED, error.getViolations().getFirst().code());
        assertEquals(0, updates());
    }

    @Test
    void guestAndOutsiderCannotMutateAssignments() {
        UUID outsider = user();
        for (UUID actor : new UUID[] {guest, outsider}) {
            assertThrows(TaskAccessException.class, () -> assignments.claim(space, actor, task));
            assertThrows(TaskAccessException.class, () -> assignments.release(space, actor, task));
            assertThrows(
                    TaskAccessException.class,
                    () -> assignments.assignDirect(space, actor, task, member));
        }
        assertEquals(0, updates());
    }

    @Test
    void rejectsOccupiedTaskAndInvalidDirectAssignee() {
        assignments.assignDirect(space, owner, task, member);
        assertThrows(TaskConflictException.class, () -> assignments.claim(space, other, task));
        assertThrows(
                FieldValidationException.class,
                () -> assignments.assignDirect(space, owner, task, guest));
        assertThrows(
                FieldValidationException.class,
                () -> assignments.assignDirect(space, owner, task, user()));
        assertEquals(1, updates());
    }

    @Test
    void repeatedDirectAssignmentAndReleaseOfFreeTaskAreNoOps() {
        var before = reading.get(space, member, task);
        assertEquals(before.updatedAt(), assignments.release(space, member, task).updatedAt());
        var direct = assignments.assignDirect(space, owner, task, member);
        assertEquals(
                direct.updatedAt(),
                assignments.assignDirect(space, owner, task, member).updatedAt());
        assertEquals(1, updates());
    }

    @Test
    void taskMustBelongToRequestedSpace() {
        UUID foreign = UUID.randomUUID();
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?,'Foreign')", foreign);
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,'OWNER')",
                foreign,
                owner);
        assertThrows(TaskNotFoundException.class, () -> assignments.claim(foreign, owner, task));
        assertThrows(TaskNotFoundException.class, () -> assignments.release(foreign, owner, task));
        assertThrows(
                TaskNotFoundException.class,
                () -> assignments.assignDirect(foreign, owner, task, owner));
    }

    @Test
    void historyFailureRollsBackClaimReleaseAndDirectAssignment() {
        doThrow(new IllegalStateException("History unavailable")).when(changes).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> assignments.claim(space, member, task));
        assertNull(reading.get(space, owner, task).assigneeId());
        assertThrows(
                IllegalStateException.class,
                () -> assignments.assignDirect(space, owner, task, member));
        assertNull(reading.get(space, owner, task).assigneeId());
        jdbc.update(
                "UPDATE tasks SET assignee_user_id=?,assignment_source='SELF_CLAIM' WHERE id=?",
                member,
                task);
        assertThrows(IllegalStateException.class, () -> assignments.release(space, member, task));
        assertEquals(member, reading.get(space, owner, task).assigneeId());
        assertEquals(0, updates());
    }

    @Test
    void simultaneousClaimsHaveOneWinnerAndOneHistoryEvent() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> claimAfter(ready, start, member));
            var second = pool.submit(() -> claimAfter(ready, start, other));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int winners =
                    (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                            + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
            assertEquals(
                    AssignmentSource.SELF_CLAIM,
                    reading.get(space, owner, task).assignmentSource());
            assertEquals(1, updates());
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean claimAfter(CountDownLatch ready, CountDownLatch start, UUID actor)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            assignments.claim(space, actor, task);
            return true;
        } catch (TaskConflictException occupied) {
            return false;
        }
    }

    private Map<String, Object> valueChange(Object oldValue, Object newValue) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("old", oldValue);
        values.put("new", newValue);
        return values;
    }
}
