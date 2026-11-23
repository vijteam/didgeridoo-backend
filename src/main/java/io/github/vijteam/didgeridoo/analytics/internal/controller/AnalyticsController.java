package io.github.vijteam.didgeridoo.analytics.internal.controller;

import io.github.vijteam.didgeridoo.analytics.internal.service.AnalyticsSnapshot;
import io.github.vijteam.didgeridoo.analytics.internal.service.SpaceAnalytics;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceActorResolver;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
public class AnalyticsController {
    private final SpaceActorResolver actors;
    private final SpaceAnalytics analytics;

    public AnalyticsController(SpaceActorResolver actors, SpaceAnalytics analytics) {
        this.actors = actors;
        this.analytics = analytics;
    }

    @GetMapping("/analytics")
    public AnalyticsSnapshot spaceAnalytics(
            Principal principal,
            @PathVariable String spaceId,
            @RequestParam MultiValueMap<String, String> query) {
        UUID actorId = actors.requireUser(principal);
        Period period = period(query);
        return analytics.forSpace(actorId, uuid(spaceId, "spaceId"), period.from(), period.to());
    }

    @GetMapping("/memberships/{userId}/analytics")
    public AnalyticsSnapshot memberAnalytics(
            Principal principal,
            @PathVariable String spaceId,
            @PathVariable String userId,
            @RequestParam MultiValueMap<String, String> query) {
        UUID actorId = actors.requireUser(principal);
        Period period = period(query);
        return analytics.forMember(
                actorId,
                uuid(spaceId, "spaceId"),
                uuid(userId, "userId"),
                period.from(),
                period.to());
    }

    private record Period(Instant from, Instant to) {}

    private static Period period(MultiValueMap<String, String> query) {
        if (query.isEmpty()) {
            return new Period(null, null);
        }
        query.forEach(
                (field, values) -> {
                    if (!field.equals("from") && !field.equals("to")) {
                        throw new FieldValidationException(
                                field, FieldErrorCode.UNKNOWN_FIELD, "Unknown parameter");
                    }
                    if (values.size() != 1) {
                        throw invalidPeriod(field, "Parameter must appear once");
                    }
                });
        Instant from = instant(query.getFirst("from"), "from");
        Instant to = instant(query.getFirst("to"), "to");
        if (!from.isBefore(to)) {
            throw invalidPeriod("to", "End must come after start");
        }
        return new Period(from, to);
    }

    private static Instant instant(String value, String field) {
        if (value == null || !value.matches("[0-9]{4}-.*Z")) {
            throw invalidPeriod(field, "UTC timestamp is required");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw invalidPeriod(field, "Invalid UTC timestamp");
        }
    }

    private static FieldValidationException invalidPeriod(String field, String message) {
        return new FieldValidationException(field, FieldErrorCode.INVALID_FORMAT, message);
    }

    private static UUID uuid(String value, String field) {
        UUID id;
        try {
            id = UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw invalidUuid(field);
        }
        if (!id.toString().equalsIgnoreCase(value)) {
            throw invalidUuid(field);
        }
        return id;
    }

    private static FieldValidationException invalidUuid(String field) {
        return new FieldValidationException(
                field, FieldErrorCode.INVALID_FORMAT, "Canonical UUID required");
    }
}
