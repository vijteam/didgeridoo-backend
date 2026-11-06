package io.github.vijteam.didgeridoo.tasks.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vijteam.didgeridoo.TestcontainersConfiguration;
import io.github.vijteam.didgeridoo.tasks.api.StatusInitializer;
import io.github.vijteam.didgeridoo.tasks.internal.model.StatusKind;
import io.github.vijteam.didgeridoo.tasks.internal.repository.TaskStatusRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StatusInitializationTest {
    @Autowired private StatusInitializer initializer;
    @Autowired private TaskCreationService creation;
    @Autowired private TaskStatusRepository statuses;
    @Autowired private TransactionTemplate transaction;

    @Test
    void createsTwoOrderedStatuses() {
        UUID spaceId = UUID.randomUUID();
        initializer.initialize(spaceId);
        var result = statuses.findAllBySpaceIdOrderByPositionAsc(spaceId);
        assertEquals(2, result.size());
        assertEquals(StatusKind.INITIAL, result.get(0).getKind());
        assertEquals(StatusKind.FINAL, result.get(1).getKind());
        assertNotEquals(result.get(0).getId(), result.get(1).getId());
        assertTrue(result.stream().allMatch(status -> spaceId.equals(status.getSpaceId())));
    }

    @Test
    void repeatedInitializationFailsWithoutChangingExistingStatuses() {
        UUID spaceId = UUID.randomUUID();
        initializer.initialize(spaceId);
        var before =
                statuses.findAllBySpaceIdOrderByPositionAsc(spaceId).stream()
                        .map(s -> s.getId())
                        .toList();
        assertThrows(IllegalStateException.class, () -> initializer.initialize(spaceId));
        assertEquals(
                before,
                statuses.findAllBySpaceIdOrderByPositionAsc(spaceId).stream()
                        .map(s -> s.getId())
                        .toList());
    }

    @Test
    void joinsAndRollsBackWithCallingWorkflow() {
        UUID spaceId = UUID.randomUUID();
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                tx -> {
                                    initializer.initialize(spaceId);
                                    assertEquals(
                                            2,
                                            statuses.findAllBySpaceIdOrderByPositionAsc(spaceId)
                                                    .size());
                                    throw new IllegalStateException("Workflow failed");
                                }));
        assertTrue(statuses.findAllBySpaceIdOrderByPositionAsc(spaceId).isEmpty());
    }

    @Test
    void creationFailsClosedWithoutSpacesAdapter() {
        UUID space = UUID.randomUUID();
        assertThrows(
                IllegalStateException.class,
                () ->
                        creation.create(
                                space,
                                UUID.randomUUID(),
                                new CreateTask("Task", null, null, null, null, null, false)));
    }

    @Test
    void rejectsNullSpace() {
        assertThrows(IllegalArgumentException.class, () -> initializer.initialize(null));
    }
}
