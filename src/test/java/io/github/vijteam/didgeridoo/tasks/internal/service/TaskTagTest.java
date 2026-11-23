package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskConflictException;
import io.github.vijteam.didgeridoo.tasks.internal.model.SpaceTag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskTagTest {
    @Autowired private TaskTagService tags;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskReadingService reading;
    @Autowired private TaskHistoryService history;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    private final UUID space = UUID.randomUUID();
    private final UUID otherSpace = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID assignee = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();

    @BeforeEach
    void setup() {
        addUser(owner);
        addUser(assignee);
        addUser(guest);
        addSpace(space);
        addSpace(otherSpace);
        addMember(space, assignee, "MEMBER");
        addMember(space, guest, "GUEST");
    }

    @Test
    void createWithTagsAndFilterBeforePaging() {
        var urgent = tags.create(space, owner, "Urgent");
        var home = tags.create(space, owner, "Home");
        UUID tagged = createTask("Tagged", List.of(urgent.id(), home.id()), true);
        createTask("Other", List.of(home.id()), false);

        assertEquals(List.of(home, urgent), tags.forTask(space, guest, tagged));
        var event = history.list(space, owner, tagged, 0, 20).getContent().getFirst();
        assertEquals("CREATED", event.kind());
        assertEquals("Urgent", event.changedFields().get("tags." + urgent.id()).get("new"));
        var query = new TaskSearch(null, null, null, null, null, null, urgent.id());
        assertEquals(
                List.of(tagged),
                reading.search(space, guest, query, 0, 1).getContent().stream()
                        .map(TaskView::id)
                        .toList());
    }

    @Test
    void rejectsCrossSpaceTagsAndDuplicateNamesAndChecksTaskEditPermission() {
        var foreign = tags.create(otherSpace, owner, "Foreign");
        var tag = tags.create(space, owner, "Local");
        assertThrows(TaskConflictException.class, () -> tags.create(space, owner, "Local"));
        assertThrows(
                FieldValidationException.class,
                () -> createTask("Invalid", List.of(foreign.id()), true));
        UUID editable = createTask("Editable", List.of(), true);
        UUID locked = createTask("Locked", List.of(), false);
        assertThrows(
                TaskAccessException.class,
                () -> tags.replace(space, assignee, locked, List.of(tag.id())));
        assertThrows(TaskAccessException.class, () -> tags.create(space, guest, "Forbidden"));

        tags.replace(space, assignee, editable, List.of(tag.id()));
        tags.replace(space, assignee, editable, List.of(tag.id()));

        assertEquals(List.of(tag), tags.forTask(space, guest, editable));
        assertEquals(2L, history.list(space, guest, editable, 0, 20).getTotalElements());
        assertEquals(2L, reading.list(space, guest, 0, 20).getTotalElements());
    }

    @Test
    void deletesTagAfterImpactCheckKeepsTaskAndRecordsRemovedName() {
        var tag = tags.create(space, owner, "Before");
        UUID task = createTask("Task", List.of(tag.id()), true);
        tags.rename(space, owner, tag.id(), "After");
        assertEquals(
                List.of("After"), tags.list(space, guest).stream().map(SpaceTag::name).toList());
        assertEquals(1L, tags.deletionImpact(space, owner, tag.id()));
        assertThrows(TaskConflictException.class, () -> tags.delete(space, owner, tag.id(), 0));

        tags.delete(space, owner, tag.id(), 1);

        assertNotNull(reading.get(space, guest, task));
        assertEquals(List.of(), tags.forTask(space, guest, task));
        var change = history.list(space, guest, task, 0, 20).getContent().getLast();
        assertEquals("After", change.changedFields().get("tags." + tag.id()).get("old"));
        assertNull(change.changedFields().get("tags." + tag.id()).get("new"));
    }

    private UUID createTask(String title, List<UUID> tagIds, boolean canEdit) {
        return creation.create(
                space,
                owner,
                new CreateTask(title, null, null, null, null, assignee, canEdit, tagIds));
    }

    private void addSpace(UUID spaceId) {
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?, 'Tags')", spaceId);
        addMember(spaceId, owner, "OWNER");
        initializer.initialize(spaceId);
    }

    private void addMember(UUID spaceId, UUID userId, String role) {
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role)",
                spaceId,
                userId,
                role);
    }

    private void addUser(UUID actor) {
        jdbc.update(
                "INSERT INTO users(id, username, display_name, email, password_hash) VALUES (?, ?, 'Test', ?, 'hash')",
                actor,
                actor.toString().substring(0, 20),
                actor + "@test.example");
    }
}
