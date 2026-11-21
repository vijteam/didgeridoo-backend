package io.github.vijteam.didgeridoo.tasks.internal.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.service.CreateTask;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskCreationService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskDeletionService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskEditingService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskHistoryService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskPatch;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskReadingService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskSpaceAccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** SQL below creates test fixtures only; production tasks never writes spaces tables. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TaskSpaceAccessIntegrationTest {
    @Autowired private TaskSpaceAccess access;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskEditingService editing;
    @Autowired private TaskDeletionService deletion;
    @Autowired private TaskReadingService reading;
    @Autowired private TaskHistoryService history;
    @Autowired private StatusInitializer initializer;
    @Autowired private JdbcTemplate jdbc;
    private UUID space;
    private UUID owner;
    private UUID member;
    private UUID guest;
    private UUID outsider;

    @BeforeEach
    void setup() {
        space = UUID.randomUUID();
        owner = user();
        member = user();
        guest = user();
        outsider = user();
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?, 'Integration')", space);
        membership(owner, "OWNER");
        membership(member, "MEMBER");
        membership(guest, "GUEST");
        initializer.initialize(space);
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

    private void membership(UUID user, String role) {
        jdbc.update(
                "INSERT INTO space_memberships(space_id,user_id,role) VALUES (?,?,?::space_role)",
                space,
                user,
                role);
    }

    private CreateTask command(UUID assignee) {
        return new CreateTask("Real membership", null, null, null, null, assignee, false);
    }

    @Test
    void ownerCreatesForMemberAndGuestReadsTaskAndHistory() {
        UUID task = creation.create(space, owner, command(member));
        assertEquals(member, reading.get(space, guest, task).assigneeId());
        assertEquals(1, history.list(space, guest, task, 0, 20).getTotalElements());
        assertEquals(TaskSpaceAccess.Role.GUEST, access.roleOf(space, guest));
    }

    @Test
    void memberCreatesForSelfButCannotAssignAnotherUser() {
        UUID task = creation.create(space, member, command(member));
        assertEquals(member, reading.get(space, member, task).assigneeId());
        assertEquals(
                ApiErrorCode.FORBIDDEN,
                assertThrows(
                                TaskAccessException.class,
                                () -> creation.create(space, member, command(owner)))
                        .getCode());
    }

    @Test
    void guestAndOutsiderCannotCreate() {
        assertEquals(
                ApiErrorCode.FORBIDDEN,
                assertThrows(
                                TaskAccessException.class,
                                () -> creation.create(space, guest, command(null)))
                        .getCode());
        assertEquals(
                ApiErrorCode.NOT_FOUND,
                assertThrows(
                                TaskAccessException.class,
                                () -> creation.create(space, outsider, command(null)))
                        .getCode());
    }

    @Test
    void removingMembershipRevokesAccessOnNextOperation() {
        UUID task = creation.create(space, owner, command(null));
        reading.get(space, member, task);
        jdbc.update("DELETE FROM space_memberships WHERE space_id=? AND user_id=?", space, member);
        assertEquals(
                ApiErrorCode.NOT_FOUND,
                assertThrows(TaskAccessException.class, () -> reading.get(space, member, task))
                        .getCode());
        assertEquals(
                ApiErrorCode.NOT_FOUND,
                assertThrows(
                                TaskAccessException.class,
                                () -> history.list(space, member, task, 0, 20))
                        .getCode());
    }

    @Test
    void downgradingCreatorToGuestRemovesWritePermission() {
        UUID task = creation.create(space, member, command(null));
        jdbc.update(
                "UPDATE space_memberships SET role='GUEST' WHERE space_id=? AND user_id=?",
                space,
                member);
        assertEquals(task, reading.get(space, member, task).id());
        assertEquals(
                ApiErrorCode.FORBIDDEN,
                assertThrows(
                                TaskAccessException.class,
                                () -> creation.create(space, member, command(null)))
                        .getCode());
        assertEquals(
                ApiErrorCode.FORBIDDEN,
                assertThrows(
                                TaskAccessException.class,
                                () ->
                                        editing.patch(
                                                space,
                                                member,
                                                task,
                                                new TaskPatch(
                                                        TaskPatch.Field.of("Forbidden"),
                                                        null,
                                                        null,
                                                        null,
                                                        null)))
                        .getCode());
        assertEquals(
                ApiErrorCode.FORBIDDEN,
                assertThrows(TaskAccessException.class, () -> deletion.delete(space, member, task))
                        .getCode());
    }

    @Test
    void membershipInOneSpaceDoesNotGrantAccessToAnother() {
        UUID foreign = UUID.randomUUID();
        jdbc.update("INSERT INTO spaces(id,name) VALUES (?, 'Other space')", foreign);
        assertEquals(
                ApiErrorCode.NOT_FOUND,
                assertThrows(TaskAccessException.class, () -> reading.list(foreign, owner, 0, 20))
                        .getCode());
    }
}
