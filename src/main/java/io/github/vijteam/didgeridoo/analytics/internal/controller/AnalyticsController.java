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
        rejectQueryParameters(query);
        return analytics.forSpace(actorId, uuid(spaceId, "spaceId"));
    }

    @GetMapping("/memberships/{userId}/analytics")
    public AnalyticsSnapshot memberAnalytics(
            Principal principal,
            @PathVariable String spaceId,
            @PathVariable String userId,
            @RequestParam MultiValueMap<String, String> query) {
        UUID actorId = actors.requireUser(principal);
        rejectQueryParameters(query);
        return analytics.forMember(actorId, uuid(spaceId, "spaceId"), uuid(userId, "userId"));
    }

    private static void rejectQueryParameters(MultiValueMap<String, String> query) {
        if (!query.isEmpty()) {
            throw new FieldValidationException(
                    "query", FieldErrorCode.UNKNOWN_FIELD, "Query parameters are not supported");
        }
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
