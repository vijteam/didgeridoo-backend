package io.github.vijteam.didgeridoo.workflows.internal.controller;

import io.github.vijteam.didgeridoo.spaces.api.SpaceActorResolver;
import io.github.vijteam.didgeridoo.spaces.api.SpaceMemberSummary;
import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceMembershipWorkflow;
import io.github.vijteam.didgeridoo.workflows.internal.service.SpaceMembershipWorkflow.Departure;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
public class SpaceMembershipController {
    public record AssignmentImpact(long affectedCount) {}

    private final SpaceActorResolver actors;
    private final SpaceRequestParser requests;
    private final SpaceMembershipWorkflow workflow;

    public SpaceMembershipController(
            SpaceActorResolver actors,
            SpaceRequestParser requests,
            SpaceMembershipWorkflow workflow) {
        this.actors = actors;
        this.requests = requests;
        this.workflow = workflow;
    }

    @GetMapping("/memberships/{userId}/assignment-impact")
    public AssignmentImpact impact(
            Principal principal,
            @PathVariable String spaceId,
            @PathVariable String userId,
            @RequestParam MultiValueMap<String, String> query) {
        return new AssignmentImpact(
                workflow.assignmentImpact(
                        actors.requireUser(principal),
                        requests.resourceId(spaceId, query),
                        requests.memberId(userId, query)));
    }

    @PatchMapping(path = "/memberships/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SpaceMemberSummary changeRole(
            Principal principal,
            @PathVariable String spaceId,
            @PathVariable String userId,
            @RequestBody String body,
            @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        var space = requests.resourceId(spaceId, query);
        var member = requests.memberId(userId, query);
        var change = requests.roleChange(body);
        return workflow.updateRole(actor, space, member, change.role(), change.affectedCount());
    }

    @DeleteMapping("/memberships/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void exclude(
            Principal principal,
            @PathVariable String spaceId,
            @PathVariable String userId,
            @RequestParam MultiValueMap<String, String> query) {
        workflow.exclude(
                actors.requireUser(principal),
                requests.resourceId(spaceId, query),
                requests.memberId(userId, query));
    }

    @PostMapping(path = "/departures", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Departure depart(
            Principal principal,
            @PathVariable String spaceId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestBody String body,
            @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        var space = requests.resourceId(spaceId, query);
        var departure = requests.departure(body, key, query);
        return workflow.depart(
                actor,
                space,
                departure.requestId(),
                departure.successorId(),
                departure.deleteSpace(),
                departure.allowAutomaticDeletion());
    }
}
