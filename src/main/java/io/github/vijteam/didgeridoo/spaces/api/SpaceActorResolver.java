package io.github.vijteam.didgeridoo.spaces.api;

import java.security.Principal;
import java.util.UUID;

/** Resolves a trusted authenticated username to a current user, never a client-supplied UUID. */
public interface SpaceActorResolver {
    UUID requireUser(Principal principal);
}
