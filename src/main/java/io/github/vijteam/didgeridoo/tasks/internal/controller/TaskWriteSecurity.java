package io.github.vijteam.didgeridoo.tasks.internal.controller;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/** Consumer-owned integration port, not an authentication or CSRF implementation. */
public interface TaskWriteSecurity {
    /**
     * Verifies the trusted session AND CSRF protection for this request before returning the actor
     * UUID. Returns null for an unauthenticated session; throws TaskCsrfException for failed CSRF.
     * Implementations must use the shared security infrastructure, never a client-supplied actor
     * ID.
     */
    UUID authorize(HttpServletRequest request);
}
