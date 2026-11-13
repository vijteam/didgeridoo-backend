package io.github.vijteam.didgeridoo.tasks.internal.controller;

import java.security.Principal;
import java.util.UUID;

/** Consumer-owned integration port. Only the trusted authentication adapter supplies user IDs. */
public interface TaskActorResolver {
    /** Returns the current user's UUID or null when the principal is no longer authenticated. */
    UUID resolve(Principal principal);
}
