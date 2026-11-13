package io.github.vijteam.didgeridoo.spaces.internal.controller;

import io.github.vijteam.didgeridoo.spaces.api.*;
import io.github.vijteam.didgeridoo.spaces.internal.service.InvitationStore;
import io.github.vijteam.didgeridoo.spaces.internal.service.SpaceInvitationService;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SpaceInvitationsController {
    public record Page(List<SpaceInvitation> items, int page, int size, long totalElements) {
        public Page {
            items = List.copyOf(items);
        }
    }

    private final SpaceInvitationService invitations;
    private final SpaceActorResolver actors;
    private final SpaceApiParser parser;

    public SpaceInvitationsController(
            SpaceInvitationService invitations, SpaceActorResolver actors, SpaceApiParser parser) {
        this.invitations = invitations;
        this.actors = actors;
        this.parser = parser;
    }

    @PostMapping(value = "/spaces/{spaceId}/invitations", consumes = "application/json")
    public ResponseEntity<SpaceInvitation> create(
            Principal principal,
            @PathVariable String spaceId,
            @RequestBody String body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        var request = parser.invitation(body, key, query);
        var result =
                invitations.create(
                        actor,
                        parser.uuid(spaceId, "spaceId"),
                        request.requestId(),
                        request.username(),
                        request.role());
        return ResponseEntity.created(URI.create("/api/v1/invitations/" + result.id()))
                .body(result);
    }

    @GetMapping("/spaces/{spaceId}/invitations")
    public Page forSpace(
            Principal principal,
            @PathVariable String spaceId,
            @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        SpacePageWindow window = parser.page(query);
        return page(invitations.forSpace(actor, parser.uuid(spaceId, "spaceId"), window), window);
    }

    @GetMapping("/users/me/invitations")
    public Page mine(Principal principal, @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        SpacePageWindow window = parser.page(query);
        return page(invitations.forRecipient(actor, window), window);
    }

    @GetMapping("/invitations/{invitationId}")
    public SpaceInvitation get(
            Principal principal,
            @PathVariable String invitationId,
            @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        parser.noQuery(query);
        return invitations.get(actor, parser.uuid(invitationId, "invitationId"));
    }

    @PatchMapping(value = "/invitations/{invitationId}", consumes = "application/json")
    public SpaceInvitation decide(
            Principal principal,
            @PathVariable String invitationId,
            @RequestBody String body,
            @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        return invitations.decide(
                actor, parser.uuid(invitationId, "invitationId"), parser.decision(body, query));
    }

    private Page page(InvitationStore.Page result, SpacePageWindow window) {
        return new Page(result.items(), window.page(), window.size(), result.totalElements());
    }
}
