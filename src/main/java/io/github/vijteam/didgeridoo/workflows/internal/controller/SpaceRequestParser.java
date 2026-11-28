package io.github.vijteam.didgeridoo.workflows.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceName;

import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.UUID;

@Component
public class SpaceRequestParser {
    public record Create(UUID requestId, String name) {}

    public record Search(String name, int page, int size) {}

    private final JsonMapper mapper =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    public Create create(String body, String key, MultiValueMap<String, String> query) {
        validateQuery(query, Set.of());
        UUID requestId = uuid(key, "Idempotency-Key");
        JsonNode json = object(body);
        for (String field : json.propertyNames()) {
            if (!field.equals("name")) {
                throw new FieldValidationException(
                        field, FieldErrorCode.UNKNOWN_FIELD, "Unknown field");
            }
        }
        if (!json.hasNonNull("name")) {
            throw new FieldValidationException("name", FieldErrorCode.REQUIRED, "Name is required");
        }
        if (!json.get("name").isString()) {
            throw new FieldValidationException(
                    "name", FieldErrorCode.INVALID_TYPE, "Name must be a string");
        }
        return new Create(requestId, normalizedName(json.get("name").asString()));
    }

    public Search search(MultiValueMap<String, String> query) {
        validateQuery(query, Set.of("name", "page", "size"));
        int page = number(query, "page", 0, 0, Integer.MAX_VALUE);
        int size = number(query, "size", 20, 1, 100);
        if ((long) page * size > Integer.MAX_VALUE) {
            throw invalid("page", "Page offset exceeds supported range");
        }
        String name = query.getFirst("name");
        if (name == null || name.isEmpty()) {
            name = "";
        } else {
            name = normalizedName(name);
        }
        return new Search(name, page, size);
    }

    public UUID resourceId(String id, MultiValueMap<String, String> query) {
        validateQuery(query, Set.of());
        return uuid(id, "spaceId");
    }

    private JsonNode object(String body) {
        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (JacksonException ex) {
            throw new SpaceHttpException(ApiErrorCode.MALFORMED_JSON, "Malformed JSON request");
        }
        if (json == null || !json.isObject()) {
            throw invalid("body", "JSON object is required");
        }
        return json;
    }

    private String normalizedName(String raw) {
        try {
            return new SpaceName(raw).value();
        } catch (IllegalArgumentException ex) {
            throw invalid("name", "Invalid space name");
        }
    }

    private UUID uuid(String value, String field) {
        try {
            UUID id = UUID.fromString(value == null ? "" : value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw invalid(field, "Canonical UUID is required");
            }
            return id;
        } catch (IllegalArgumentException ex) {
            throw invalid(field, "Canonical UUID is required");
        }
    }

    private void validateQuery(MultiValueMap<String, String> query, Set<String> allowed) {
        query.forEach(
                (field, values) -> {
                    if (!allowed.contains(field) || values.size() != 1) {
                        throw invalid(field, "Unknown or repeated query parameter");
                    }
                });
    }

    private int number(
            MultiValueMap<String, String> query, String field, int fallback, int min, int max) {
        if (!query.containsKey(field)) {
            return fallback;
        }
        try {
            String raw = query.getFirst(field);
            if (raw == null || !raw.matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            throw invalid(field, "Invalid pagination");
        }
    }

    private FieldValidationException invalid(String field, String message) {
        return new FieldValidationException(field, FieldErrorCode.INVALID_FORMAT, message);
    }
}
