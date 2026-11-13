package io.github.vijteam.didgeridoo.tasks.internal.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

class TaskRequestParserTest {
    private final TaskRequestParser parser = new TaskRequestParser();

    @Test
    void createsCommandWithoutInventingActorOrStatus() {
        var command = parser.create("{\"title\":\"Task\"}");
        assertEquals("Task", command.title());
        assertNull(command.priority());
        assertNull(command.statusId());
        assertNull(command.assigneeId());
        assertFalse(command.assigneeCanEdit());
    }

    @Test
    void patchDistinguishesOmissionAndExplicitNull() {
        var patch = parser.patch("{\"description\":null}");
        assertNull(patch.title());
        assertNull(patch.description().value());
        assertNull(patch.deadline());
        assertNull(patch.priority());
    }

    @Test
    void parsesDeadlineWithOffsetAndCanonicalStatusId() {
        var command =
                parser.create("{\"title\":\"Task\",\"deadline\":\"2026-11-08T15:00:00+03:00\"}");
        assertEquals(Instant.parse("2026-11-08T12:00:00Z"), command.deadline());
        UUID id = UUID.randomUUID();
        assertEquals(id, parser.status("{\"statusId\":\"" + id + "\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{} {}", "{\"title\":\"A\",\"title\":\"B\"}"})
    void rejectsMalformedOrDuplicateJson(String body) {
        assertThrows(TaskMalformedJsonException.class, () -> parser.create(body));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "[]",
                "true",
                "{}",
                "{\"title\":1}",
                "{\"title\":null}",
                "{\"title\":\"Task\",\"actorId\":\"fake\"}",
                "{\"title\":\"Task\",\"priority\":null}",
                "{\"title\":\"Task\",\"priority\":\"high\"}",
                "{\"title\":\"Task\",\"assigneeCanEdit\":\"true\"}",
                "{\"title\":\"Task\",\"statusId\":null}",
                "{\"title\":\"Task\",\"statusId\":\"1-1-1-1-1\"}",
                "{\"title\":\"Task\",\"deadline\":\"2026-11-08T15:00:00\"}"
            })
    void rejectsInvalidCreateShapesAndTypes(String body) {
        assertThrows(TaskValidationException.class, () -> parser.create(body));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"priority\":null}",
                "{\"assigneeCanEdit\":null}",
                "{\"title\":null}",
                "{\"statusId\":\"fake\"}",
                "{\"assigneeId\":null}",
                "{\"description\":42}"
            })
    void rejectsInvalidPatch(String body) {
        assertThrows(TaskValidationException.class, () -> parser.patch(body));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"statusId\":null}",
                "{\"statusId\":true}",
                "{\"statusId\":\"bad\"}",
                "{\"statusId\":\"00000000-0000-0000-0000-000000000000\",\"actorId\":\"fake\"}"
            })
    void rejectsInvalidStatusRequest(String body) {
        assertThrows(TaskValidationException.class, () -> parser.status(body));
    }

    @Test
    void parsesAllExplicitCreateFieldsWithoutCoercion() {
        UUID status = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        var command =
                parser.create(
                        "{\"title\":\"Task\",\"description\":\"Details\",\"priority\":\"HIGH\",\"assigneeCanEdit\":true,\"statusId\":\""
                                + status
                                + "\",\"assigneeId\":\""
                                + assignee
                                + "\",\"deadline\":null}");
        assertEquals(status, command.statusId());
        assertEquals(assignee, command.assigneeId());
        assertEquals("HIGH", command.priority().name());
        assertEquals(true, command.assigneeCanEdit());
        assertEquals("Details", command.description());
        assertNull(command.deadline());
    }
}
