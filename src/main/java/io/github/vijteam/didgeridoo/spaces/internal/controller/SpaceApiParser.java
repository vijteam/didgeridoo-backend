package io.github.vijteam.didgeridoo.spaces.internal.controller;

import io.github.vijteam.didgeridoo.shared.*;
import io.github.vijteam.didgeridoo.spaces.api.*;

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
public class SpaceApiParser {
    public record InvitationRequest(UUID requestId, String username, SpaceRole role) {}

    private final JsonMapper mapper =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    public UUID uuid(String value, String field) {
        try {
            UUID id = UUID.fromString(value == null ? "" : value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw invalid(field, "Canonical UUID required");
            }
            return id;
        } catch (IllegalArgumentException ex) {
            throw invalid(field, "Canonical UUID required");
        }
    }

    public SpacePageWindow page(MultiValueMap<String, String> query) {
        query(query, Set.of("page", "size"));
        int page = number(query, "page", 0);
        int size = number(query, "size", 20);
        if (size < 1 || size > 100) {
            throw invalid("size", "Size must be between 1 and 100");
        }
        try {
            return new SpacePageWindow(page, size);
        } catch (IllegalArgumentException ex) {
            throw invalid("page", "Invalid page offset");
        }
    }

    public void noQuery(MultiValueMap<String, String> query) {
        query(query, Set.of());
    }

    public InvitationRequest invitation(
            String body, String key, MultiValueMap<String, String> query) {
        noQuery(query);
        UUID requestId = uuid(key, "Idempotency-Key");
        JsonNode json = object(body, Set.of("username", "role"));
        String username = string(json, "username");
        if (!username.matches("[A-Za-z][A-Za-z0-9]*(?:[._-][A-Za-z0-9]+)*")
                || username.length() < 3
                || username.length() > 30) {
            throw invalid("username", "Invalid username");
        }
        SpaceRole role = SpaceRole.MEMBER;
        if (json.has("role")) {
            try {
                role = SpaceRole.valueOf(string(json, "role"));
            } catch (IllegalArgumentException ex) {
                throw invalid("role", "Invalid role");
            }
        }
        return new InvitationRequest(requestId, username, role);
    }

    public InvitationState decision(String body, MultiValueMap<String, String> query) {
        noQuery(query);
        JsonNode json = object(body, Set.of("decision"));
        try {
            InvitationState state = InvitationState.valueOf(string(json, "decision"));
            if (state == InvitationState.PENDING) {
                throw invalid("decision", "Terminal decision required");
            }
            return state;
        } catch (IllegalArgumentException ex) {
            throw invalid("decision", "Invalid decision");
        }
    }

    private JsonNode object(String body, Set<String> fields) {
        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (JacksonException ex) {
            throw new SpaceRequestException(ApiErrorCode.MALFORMED_JSON, "Malformed JSON request");
        }
        if (json == null || !json.isObject()) {
            throw invalid("body", "JSON object required");
        }
        for (String field : json.propertyNames()) {
            if (!fields.contains(field)) {
                throw new FieldValidationException(
                        field, FieldErrorCode.UNKNOWN_FIELD, "Unknown field");
            }
        }
        return json;
    }

    private String string(JsonNode json, String field) {
        if (!json.hasNonNull(field)) {
            throw new FieldValidationException(field, FieldErrorCode.REQUIRED, "Field required");
        }
        if (!json.get(field).isString()) {
            throw new FieldValidationException(
                    field, FieldErrorCode.INVALID_TYPE, "String required");
        }
        return json.get(field).asString();
    }

    private int number(MultiValueMap<String, String> query, String field, int fallback) {
        if (!query.containsKey(field)) {
            return fallback;
        }
        String value = query.getFirst(field);
        try {
            if (value == null || !value.matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw invalid(field, "Invalid pagination");
        }
    }

    private void query(MultiValueMap<String, String> query, Set<String> allowed) {
        query.forEach(
                (field, values) -> {
                    if (!allowed.contains(field) || values.size() != 1) {
                        throw invalid(field, "Unknown or repeated parameter");
                    }
                });
    }

    private FieldValidationException invalid(String field, String message) {
        return new FieldValidationException(field, FieldErrorCode.INVALID_FORMAT, message);
    }
}
