package io.github.vijteam.didgeridoo.tasks.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

class TaskStatusTest {
    private final UUID spaceId = UUID.randomUUID();

    @Test
    void shouldAllowRenamingFinalStatusWithoutChangingItsKind() {
        TaskStatus status = new TaskStatus(spaceId, "Готово", StatusKind.FINAL, 1);
        status.rename("Проверено");
        assertEquals("Проверено", status.getName());
        assertEquals(StatusKind.FINAL, status.getKind());
        assertEquals(1, status.getPosition());
        assertEquals(spaceId, status.getSpaceId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectBlankStatusName(String name) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskStatus(spaceId, name, StatusKind.INITIAL, 0));
    }

    @Test
    void shouldEnforceStatusNameLengthAndPreserveNameOnFailure() {
        TaskStatus status = new TaskStatus(spaceId, "🚀".repeat(100), StatusKind.INITIAL, 0);
        assertThrows(IllegalArgumentException.class, () -> status.rename("🚀".repeat(101)));
        assertEquals("🚀".repeat(100), status.getName());
    }

    @Test
    void shouldRequireSpaceKindAndNonnegativePosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskStatus(null, "New", StatusKind.INITIAL, 0));
        assertThrows(IllegalArgumentException.class, () -> new TaskStatus(spaceId, "New", null, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskStatus(spaceId, "New", StatusKind.INITIAL, -1));
    }
}
