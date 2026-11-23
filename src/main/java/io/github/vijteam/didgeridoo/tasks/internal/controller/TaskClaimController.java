package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.spaces.api.SpaceActorResolver;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskAssignmentService;
import io.github.vijteam.didgeridoo.tasks.internal.service.TaskView;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/tasks/{taskId}/claim")
public class TaskClaimController {
    private final SpaceActorResolver actors;
    private final TaskAssignmentService assignments;
    private final TaskRequestParser parser;

    public TaskClaimController(
            SpaceActorResolver actors,
            TaskAssignmentService assignments,
            TaskRequestParser parser) {
        this.actors = actors;
        this.assignments = assignments;
        this.parser = parser;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public TaskView claim(
            Principal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID taskId,
            @RequestBody String body,
            @RequestParam MultiValueMap<String, String> query) {
        UUID actor = actor(principal, query);
        parser.claim(body);
        return assignments.claim(spaceId, actor, taskId);
    }

    @DeleteMapping
    public TaskView release(
            Principal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID taskId,
            @RequestParam MultiValueMap<String, String> query) {
        return assignments.release(spaceId, actor(principal, query), taskId);
    }

    private UUID actor(Principal principal, MultiValueMap<String, String> query) {
        UUID actor = actors.requireUser(principal);
        if (!query.isEmpty()) {
            throw new FieldValidationException(
                    query.keySet().iterator().next(),
                    FieldErrorCode.UNKNOWN_FIELD,
                    "Query parameters are not supported");
        }
        return actor;
    }
}
