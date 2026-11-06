package io.github.vijteam.didgeridoo.tasks.internal.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskChangeRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskRepository;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;
import io.github.vijteam.didgeridoo.tasks.internal.service.CreateTask;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskCreationService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskReadingService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskSpaceAccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.UUID;

class TaskErrorTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<TaskSpaceAccess> provider = mock(ObjectProvider.class);

    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskStatusRepository statuses = mock(TaskStatusRepository.class);
    private final TaskSpaceAccess access = mock(TaskSpaceAccess.class);
    private final UUID space = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @Test
    void missingAdapterHasTypedDependencyReason() {
        var reading = new TaskReadingService(tasks, provider);
        assertEquals(
                TaskDependencyException.Reason.SPACES_UNAVAILABLE,
                assertThrows(TaskDependencyException.class, () -> reading.list(space, actor, 0, 20))
                        .getReason());
    }

    @Test
    void outsiderIsDistinguishableFromForbiddenAction() {
        when(provider.getIfAvailable()).thenReturn(access);
        var reading = new TaskReadingService(tasks, provider);
        assertEquals(
                TaskAccessException.Reason.NOT_MEMBER,
                assertThrows(TaskAccessException.class, () -> reading.list(space, actor, 0, 20))
                        .getReason());
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.GUEST);
        var creation =
                new TaskCreationService(
                        tasks, statuses, mock(TaskChangeRepository.class), provider);
        assertEquals(
                TaskAccessException.Reason.FORBIDDEN,
                assertThrows(
                                TaskAccessException.class,
                                () ->
                                        creation.create(
                                                space,
                                                actor,
                                                new CreateTask(
                                                        "Task", null, null, null, null, null,
                                                        false)))
                        .getReason());
    }

    @Test
    void missingTaskAndInvalidInputHaveDifferentTypes() {
        when(provider.getIfAvailable()).thenReturn(access);
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.MEMBER);
        var reading = new TaskReadingService(tasks, provider);
        assertThrows(
                TaskNotFoundException.class, () -> reading.get(space, actor, UUID.randomUUID()));
        assertThrows(TaskValidationException.class, () -> reading.list(space, actor, -1, 20));
    }

    @Test
    void missingInitialIsDependencyFailureNotInvalidStatus() {
        when(provider.getIfAvailable()).thenReturn(access);
        when(access.roleOf(space, actor)).thenReturn(TaskSpaceAccess.Role.MEMBER);
        var creation =
                new TaskCreationService(
                        tasks, statuses, mock(TaskChangeRepository.class), provider);
        var command = new CreateTask("Task", null, null, null, null, null, false);
        assertEquals(
                TaskDependencyException.Reason.INITIAL_STATUS_MISSING,
                assertThrows(
                                TaskDependencyException.class,
                                () -> creation.create(space, actor, command))
                        .getReason());
        var explicit = new CreateTask("Task", null, null, null, UUID.randomUUID(), null, false);
        assertEquals(
                "statusId",
                assertThrows(
                                TaskValidationException.class,
                                () -> creation.create(space, actor, explicit))
                        .getField());
    }
}
