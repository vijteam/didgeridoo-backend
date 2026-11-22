package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskSearchTest {
    @Autowired private TaskReadingService reading;
    @Autowired private TaskCreationService creation;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    private final UUID owner = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();
    private final UUID space = UUID.randomUUID();
    private final UUID otherSpace = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-11-21T00:00:00Z");
    private final Instant end = Instant.parse("2026-11-22T00:00:00Z");
    private UUID initial;
    private UUID done;

    @BeforeEach
    void setup() {
        addUser(owner);
        addUser(guest);
        addSpace(space);
        addSpace(otherSpace);
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,'GUEST')",
                space,
                guest);
        initial = status("INITIAL");
        done = status("FINAL");
    }

    @Test
    void filtersByLiteralTextAssigneeDeadlineAndSpaceBeforePagination() {
        UUID match = task(space, "Generic", "Fix NaME_100%", start, initial, owner);
        task(space, "nameX100Y", null, start, initial, owner);
        task(space, "name_100%", null, end, initial, owner);
        task(space, "name_100%", null, start, initial, null);
        task(otherSpace, "name_100%", null, start, null, owner);
        var filter =
                new TaskSearch("name_100%", owner, initial, start, end, TaskSearch.Order.DEADLINE);

        var page = reading.search(space, guest, filter, 0, 1);

        assertEquals(List.of(match), page.getContent().stream().map(TaskView::id).toList());
        assertEquals(1L, page.getTotalElements());
        assertThrows(
                TaskAccessException.class, () -> reading.search(otherSpace, guest, filter, 0, 1));
    }

    @Test
    void sortsByDeadlineWithNullLastAndByStatusPosition() {
        UUID unscheduled = task(space, "Unscheduled", null, null, initial, null);
        UUID late = task(space, "Late", null, end, initial, null);
        UUID early = task(space, "Early", null, start, done, null);
        var deadlines = new TaskSearch(null, null, null, null, null, TaskSearch.Order.DEADLINE);
        var statuses = new TaskSearch(null, null, null, null, null, TaskSearch.Order.STATUS);

        assertEquals(
                List.of(early, late, unscheduled),
                reading.search(space, guest, deadlines, 0, 20).getContent().stream()
                        .map(TaskView::id)
                        .toList());
        var ordered = reading.search(space, guest, statuses, 0, 20).getContent();
        assertEquals(
                List.of(initial, initial, done), ordered.stream().map(TaskView::statusId).toList());
    }

    private UUID task(
            UUID spaceId,
            String title,
            String description,
            Instant deadline,
            UUID status,
            UUID assignee) {
        return creation.create(
                spaceId,
                owner,
                new CreateTask(title, description, deadline, null, status, assignee, false));
    }

    private UUID status(String kind) {
        return jdbc.queryForObject(
                "SELECT id FROM task_statuses WHERE space_id=? AND kind=?",
                UUID.class,
                space,
                kind);
    }

    private void addSpace(UUID spaceId) {
        jdbc.update("INSERT INTO spaces(id, name) VALUES (?, 'Search')", spaceId);
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,'OWNER')",
                spaceId,
                owner);
        initializer.initialize(spaceId);
    }

    private void addUser(UUID actor) {
        jdbc.update(
                "INSERT INTO users(id, username, display_name, email, password_hash) VALUES (?, ?, 'Test', ?, 'hash')",
                actor,
                actor.toString().substring(0, 20),
                actor + "@test.example");
    }
}
