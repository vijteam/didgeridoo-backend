package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;
import io.github.vijteam.didgeridoo.tasks.internal.model.TaskPriority;
import io.github.vijteam.didgeridoo.tasks.internal.service.CreateTask;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskPatch;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

/** Tasks-local JSON boundary. Does not configure shared Jackson or expose write endpoints. */
@Component
public class TaskRequestParser {
    private static final Set<String> CONTENT =
            Set.of("title", "description", "deadline", "priority", "assigneeCanEdit");
    private static final Set<String> CREATE =
            Set.of(
                    "title",
                    "description",
                    "deadline",
                    "priority",
                    "assigneeCanEdit",
                    "statusId",
                    "assigneeId");
    private final JsonMapper mapper =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    public CreateTask create(String body) {
        JsonNode root = object(body, CREATE);
        return new CreateTask(
                string(root, "title", false),
                string(root, "description", true),
                deadline(root),
                root.has("priority") ? priority(root) : null,
                root.has("statusId") ? uuid(root, "statusId", false) : null,
                uuid(root, "assigneeId", true),
                root.has("assigneeCanEdit") && permission(root));
    }

    public TaskPatch patch(String body) {
        JsonNode root = object(body, CONTENT);
        return new TaskPatch(
                root.has("title") ? TaskPatch.Field.of(string(root, "title", false)) : null,
                root.has("description")
                        ? TaskPatch.Field.of(string(root, "description", true))
                        : null,
                root.has("deadline") ? TaskPatch.Field.of(deadline(root)) : null,
                root.has("priority") ? TaskPatch.Field.of(priority(root)) : null,
                root.has("assigneeCanEdit") ? TaskPatch.Field.of(permission(root)) : null);
    }

    public UUID status(String body) {
        return uuid(object(body, Set.of("statusId")), "statusId", false);
    }

    private JsonNode object(String body, Set<String> allowed) {
        if (body == null || body.isBlank()) {
            throw new TaskMalformedJsonException();
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JacksonException failure) {
            throw new TaskMalformedJsonException();
        }
        if (root == null || root.isMissingNode()) {
            throw new TaskMalformedJsonException();
        }
        if (!root.isObject()) {
            throw new TaskValidationException("Request must be an object");
        }
        for (String name : root.propertyNames()) {
            if (!allowed.contains(name)) {
                throw invalid(name);
            }
        }
        return root;
    }

    private String string(JsonNode root, String field, boolean nullable) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            if (nullable) {
                return null;
            }
            throw invalid(field);
        }
        if (!value.isTextual()) {
            throw invalid(field);
        }
        return value.asString();
    }

    private boolean permission(JsonNode root) {
        JsonNode value = root.get("assigneeCanEdit");
        if (value == null || !value.isBoolean()) {
            throw invalid("assigneeCanEdit");
        }
        return value.booleanValue();
    }

    private UUID uuid(JsonNode root, String field, boolean nullable) {
        String value = string(root, field, nullable);
        if (value == null) {
            return null;
        }
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw invalid(field);
            }
            return id;
        } catch (IllegalArgumentException failure) {
            throw invalid(field);
        }
    }

    private TaskPriority priority(JsonNode root) {
        String value = string(root, "priority", false);
        try {
            return TaskPriority.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw invalid("priority");
        }
    }

    private Instant deadline(JsonNode root) {
        String value = string(root, "deadline", true);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException failure) {
            throw invalid("deadline");
        }
    }

    private TaskValidationException invalid(String field) {
        return new TaskValidationException(field, "Invalid or unknown request field");
    }
}
