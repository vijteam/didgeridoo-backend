package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskConflictException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskStatusManagementTest {
    @Autowired private TaskStatusManagementService statuses;
    @Autowired private StatusInitializer initializer;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskReadingService reading;
    @Autowired private TaskHistoryService history;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private TaskChangeRepository changes;
    private final UUID space = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?, 'Statuses')", space);
        addMember(owner, "OWNER");
        addMember(guest, "GUEST");
        initializer.initialize(space);
    }

    @Test
    void ownerRenamesEndpointsAndReordersIntermediateStatusesWithoutMovingEndpoints() {
        var initial = statuses.list(space, guest).getFirst();
        var done = statuses.list(space, guest).getLast();
        var review = statuses.create(space, owner, "Review");
        var testing = statuses.create(space, owner, "Testing");
        statuses.rename(space, owner, initial.id(), "Ready");
        var order = List.of(initial.id(), testing.id(), review.id(), done.id());
        statuses.reorder(space, owner, order);
        assertEquals(order, statuses.list(space, guest).stream().map(TaskStatusView::id).toList());
        assertEquals("Ready", statuses.list(space, guest).getFirst().name());
        assertEquals(
                List.of(0, 1, 2, 3),
                statuses.list(space, guest).stream().map(TaskStatusView::position).toList());
        assertThrows(
                TaskValidationException.class,
                () ->
                        statuses.reorder(
                                space,
                                owner,
                                List.of(done.id(), testing.id(), review.id(), initial.id())));
        assertThrows(
                TaskValidationException.class,
                () ->
                        statuses.reorder(
                                space,
                                owner,
                                List.of(initial.id(), testing.id(), testing.id(), done.id())));
        assertThrows(
                TaskConflictException.class, () -> statuses.delete(space, owner, initial.id(), 0));
        assertThrows(TaskAccessException.class, () -> statuses.create(space, guest, "Forbidden"));
    }

    @Test
    void deleteCountsAndMovesTasksWithHistoryAndRejectsChangedImpact() {
        var initial = statuses.list(space, owner).getFirst();
        var review = statuses.create(space, owner, "Review");
        var done = statuses.list(space, owner).getLast();
        UUID task = createTask(review.id());
        assertEquals(1, statuses.deletionImpact(space, owner, review.id()));
        assertThrows(
                TaskConflictException.class, () -> statuses.delete(space, owner, review.id(), 0));
        assertEquals(review.id(), reading.get(space, owner, task).statusId());

        statuses.delete(space, owner, review.id(), 1);

        var moved = reading.get(space, owner, task);
        assertEquals(initial.id(), moved.statusId());
        assertEquals(owner, moved.assigneeId());
        assertEquals(
                List.of(initial.id(), done.id()),
                statuses.list(space, guest).stream().map(TaskStatusView::id).toList());
        var event = history.list(space, guest, task, 0, 20).getContent().getLast();
        assertEquals(owner, event.actorId());
        assertEquals(
                Map.of(
                        "statusId",
                        Map.of("old", review.id().toString(), "new", initial.id().toString())),
                event.changedFields());
    }

    @Test
    void historyFailureRollsBackStatusDeletionAndTaskMove() {
        var review = statuses.create(space, owner, "Review");
        UUID task = createTask(review.id());
        doThrow(new IllegalStateException("History unavailable"))
                .when(changes)
                .saveAllAndFlush(any());

        assertThrows(
                IllegalStateException.class, () -> statuses.delete(space, owner, review.id(), 1));

        assertEquals(review.id(), reading.get(space, owner, task).statusId());
        assertEquals(review.id(), statuses.list(space, guest).get(1).id());
        assertEquals(1, history.list(space, guest, task, 0, 20).getTotalElements());
    }

    private UUID createTask(UUID statusId) {
        return creation.create(
                space, owner, new CreateTask("Task", null, null, null, statusId, owner, false));
    }

    private void addMember(UUID actor, String role) {
        jdbc.update(
                "INSERT INTO users(id, username, display_name, email, password_hash) VALUES (?, ?, 'Test', ?, 'hash')",
                actor,
                actor.toString().substring(0, 20),
                actor + "@test.example");
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role)",
                space,
                actor,
                role);
    }
}
