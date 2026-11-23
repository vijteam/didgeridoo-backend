package io.github.vijteam.didgeridoo.tasks.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.internal.service.CreateTask;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskCreationService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskHistoryService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskReadingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskSpaceLifecycleTest {
    @Autowired private TaskSpaceLifecycle lifecycle;
    @Autowired private TaskCreationService creation;
    @Autowired private StatusInitializer initializer;
    @Autowired private TaskReadingService reading;
    @Autowired private TaskHistoryService history;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    private final UUID space = UUID.randomUUID();
    private final UUID otherSpace = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private UUID task;
    private UUID foreignTask;

    @BeforeEach
    void setup() {
        addUser(owner);
        addUser(member);
        task = addSpaceWithTask(space);
        foreignTask = addSpaceWithTask(otherSpace);
    }

    @Test
    void clearsOnlySpecifiedAssignmentsAndPreservesCreatorAndHistory() {
        assertEquals(1L, lifecycle.countAssignments(space, member));
        var result =
                transactions.execute(status -> lifecycle.clearAssignments(space, member, owner));

        assertEquals(1L, result.longValue());
        var cleared = reading.get(space, owner, task);
        assertNull(cleared.assigneeId());
        assertNull(cleared.assignmentSource());
        assertEquals(member, cleared.creatorId());
        assertEquals(member, reading.get(otherSpace, owner, foreignTask).assigneeId());
        var event = history.list(space, owner, task, 0, 20).getContent().getLast();
        assertEquals(owner, event.actorId());
        assertEquals("UPDATED", event.kind());
        assertNull(event.changedFields().get("assigneeId").get("new"));
    }

    @Test
    void statementsJoinCallerTransactionAndRollBackTogether() {
        assertThrows(
                IllegalTransactionStateException.class,
                () -> lifecycle.clearAssignments(space, member, owner));
        transactions.executeWithoutResult(
                status -> {
                    lifecycle.clearAssignments(space, member, owner);
                    jdbc.update(
                            "UPDATE space_memberships SET role='GUEST' WHERE space_id=? AND user_id=?",
                            space,
                            member);
                    status.setRollbackOnly();
                });

        assertEquals(member, reading.get(space, owner, task).assigneeId());
        assertEquals(1L, history.list(space, owner, task, 0, 20).getTotalElements());
        assertEquals(
                "MEMBER",
                jdbc.queryForObject(
                        "SELECT role::text FROM space_memberships WHERE space_id=? AND user_id=?",
                        String.class,
                        space,
                        member));
    }

    @Test
    void deletesOnlyScopedTaskDataAndLeavesMembershipToCaller() {
        transactions.executeWithoutResult(status -> lifecycle.deleteSpaceData(space));

        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM tasks WHERE space_id=?", Long.class, space));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_statuses WHERE space_id=?", Long.class, space));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM task_changes WHERE task_id=?", Long.class, task));
        assertEquals(foreignTask, reading.get(otherSpace, owner, foreignTask).id());
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM space_memberships WHERE space_id=?",
                        Long.class,
                        space));
    }

    private UUID addSpaceWithTask(UUID spaceId) {
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?, 'Lifecycle')", spaceId);
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,'OWNER'), (?,?,'MEMBER')",
                spaceId,
                owner,
                spaceId,
                member);
        initializer.initialize(spaceId);
        return creation.create(
                spaceId, member, new CreateTask("Task", null, null, null, null, member, false));
    }

    private void addUser(UUID actor) {
        jdbc.update(
                "INSERT INTO users(id, username, display_name, email, password_hash) VALUES (?, ?, 'Test', ?, 'hash')",
                actor,
                actor.toString().substring(0, 20),
                actor + "@test.example");
    }
}
