package io.github.vijteam.didgeridoo.spaces.internal.controller;

import io.github.vijteam.didgeridoo.spaces.api.*;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/memberships")
public class SpaceMembersController {
    private final SpaceMembers members;
    private final SpaceActorResolver actors;
    private final SpaceApiParser parser;

    public SpaceMembersController(
            SpaceMembers members, SpaceActorResolver actors, SpaceApiParser parser) {
        this.members = members;
        this.actors = actors;
        this.parser = parser;
    }

    @GetMapping
    public SpaceMembers.Page list(
            Principal principal,
            @PathVariable String spaceId,
            @RequestParam MultiValueMap<String, String> query) {
        var actor = actors.requireUser(principal);
        return members.list(actor, parser.uuid(spaceId, "spaceId"), parser.page(query));
    }
}
