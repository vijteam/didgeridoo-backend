package io.github.vijteam.didgeridoo.tasks.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.internal.service.CreateTask;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskCreationService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskDeletionService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskStatusChangeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskQueriesTest {
    @Autowired private TaskQueries queries;
    @Autowired private StatusInitializer initializer;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskStatusChangeService statuses;
    @Autowired private TaskDeletionService deletion;
    @Autowired private JdbcTemplate jdbc;
    private UUID space;
    private UUID owner;
    private UUID member;

    @BeforeEach
    void setup() {
        owner = user();
        member = user();
        space = space();
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,'MEMBER')",
                space,
                member);
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

    private UUID space() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?,'Queries')", id);
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,'OWNER')",
                id,
                owner);
        initializer.initialize(id);
        return id;
    }

    private UUID task(UUID target, UUID assignee) {
        return creation.create(
                target, owner, new CreateTask("Task", null, null, null, null, assignee, false));
    }

    @Test
    void includesEmptyStatusesInPositionOrder() {
        var result = queries.countByStatus(space);
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).position());
        assertEquals(1, result.get(1).position());
        assertEquals(0L, result.get(0).taskCount());
        assertEquals(0L, result.get(1).taskCount());
    }

    @Test
    void countsAssignedAndFreeTasksWithoutDuplicatingRows() {
        task(space, null);
        task(space, member);
        task(space, owner);
        assertEquals(3L, queries.countByStatus(space).getFirst().taskCount());
        assertEquals(1L, queries.countByStatusForAssignee(space, member).getFirst().taskCount());
        assertEquals(1L, queries.countByStatusForAssignee(space, owner).getFirst().taskCount());
        assertEquals(0L, queries.countByStatusForAssignee(space, member).get(1).taskCount());
    }

    @Test
    void neverCountsTasksFromAnotherSpace() {
        UUID foreign = space();
        task(foreign, null);
        task(foreign, null);
        task(space, null);
        assertEquals(1L, queries.countByStatus(space).getFirst().taskCount());
        assertEquals(2L, queries.countByStatus(foreign).getFirst().taskCount());
    }

    @Test
    void snapshotReflectsStatusChangeAndDeletion() {
        UUID task = task(space, member);
        UUID finalStatus = queries.countByStatus(space).get(1).statusId();
        statuses.change(space, owner, task, finalStatus);
        assertEquals(0L, queries.countByStatus(space).getFirst().taskCount());
        assertEquals(1L, queries.countByStatusForAssignee(space, member).get(1).taskCount());
        deletion.delete(space, owner, task);
        assertTrue(queries.countByStatus(space).stream().allMatch(row -> row.taskCount() == 0));
    }

    @Test
    void unknownSpaceReturnsNoRowsForTrustedCallerToInterpret() {
        assertTrue(queries.countByStatus(UUID.randomUUID()).isEmpty());
    }
}
