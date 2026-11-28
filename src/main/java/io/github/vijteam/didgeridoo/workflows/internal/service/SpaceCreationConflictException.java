package io.github.vijteam.didgeridoo.workflows.internal.service;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class SpaceCreationConflictException extends ApplicationException {
    public SpaceCreationConflictException() {
        super(
                ApiErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency key was already used with a different name");
    }
}
