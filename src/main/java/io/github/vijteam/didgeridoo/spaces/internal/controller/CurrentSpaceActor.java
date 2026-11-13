package io.github.vijteam.didgeridoo.spaces.internal.controller;

import io.github.vijteam.didgeridoo.identity.api.UserDirectory;
import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.spaces.api.SpaceActorResolver;

import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

@Component
public class CurrentSpaceActor implements SpaceActorResolver {
    private final UserDirectory users;

    public CurrentSpaceActor(UserDirectory users) {
        this.users = users;
    }

    @Override
    public UUID requireUser(Principal principal) {
        if (principal == null) {
            throw new SpaceRequestException(
                    ApiErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        return users.findByUsername(principal.getName())
                .orElseThrow(
                        () ->
                                new SpaceRequestException(
                                        ApiErrorCode.UNAUTHENTICATED, "Authentication required"))
                .id();
    }
}
