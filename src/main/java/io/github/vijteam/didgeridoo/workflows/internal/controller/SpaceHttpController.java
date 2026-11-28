package io.github.vijteam.didgeridoo.workflows.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.web.ApiError;
import io.github.vijteam.didgeridoo.spaces.api.SpaceActorResolver;
import io.github.vijteam.didgeridoo.spaces.api.SpaceQueries;
import io.github.vijteam.didgeridoo.spaces.api.SpaceSummary;
import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceCreationWorkflow;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces")
public class SpaceHttpController {
    private final SpaceQueries spaces;
    private final SpaceCreationWorkflow creation;
    private final SpaceActorResolver actors;
    private final SpaceRequestParser parser;

    public SpaceHttpController(
            SpaceQueries spaces,
            SpaceCreationWorkflow creation,
            SpaceActorResolver actors,
            SpaceRequestParser parser) {
        this.spaces = spaces;
        this.creation = creation;
        this.actors = actors;
        this.parser = parser;
    }

    public record SpacePage(List<SpaceSummary> items, int page, int size, long totalElements) {
        public SpacePage {
            items = List.copyOf(items);
        }
    }

    @GetMapping
    public SpacePage list(Principal principal, @RequestParam MultiValueMap<String, String> query) {
        UUID actor = actors.requireUser(principal);
        SpaceRequestParser.Search search = parser.search(query);
        var result = spaces.search(actor, search.name(), search.page(), search.size());
        return new SpacePage(
                result.getContent(), search.page(), search.size(), result.getTotalElements());
    }

    @GetMapping("/{spaceId}")
    public SpaceSummary get(
            Principal principal,
            @PathVariable String spaceId,
            @RequestParam MultiValueMap<String, String> query) {
        UUID actor = actors.requireUser(principal);
        return spaces.get(actor, parser.resourceId(spaceId, query));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<SpaceSummary> create(
            Principal principal,
            @RequestBody String body,
            @RequestHeader(name = "Idempotency-Key", required = false) String requestKey,
            @RequestParam MultiValueMap<String, String> query) {
        UUID actor = actors.requireUser(principal);
        SpaceRequestParser.Create command = parser.create(body, requestKey, query);
        SpaceSummary result = creation.create(actor, command.requestId(), command.name());
        return ResponseEntity.created(URI.create("/api/v1/spaces/" + result.id())).body(result);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> storageFailure(DataAccessException ex) {
        org.slf4j.LoggerFactory.getLogger(SpaceHttpController.class)
                .error("Space storage operation failed", ex);
        return ResponseEntity.internalServerError()
                .body(new ApiError(ApiErrorCode.INTERNAL_ERROR, "Unable to process request"));
    }
}
