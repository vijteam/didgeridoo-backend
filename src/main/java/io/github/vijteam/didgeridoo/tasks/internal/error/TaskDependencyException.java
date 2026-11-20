package io.github.vijteam.didgeridoo.tasks.internal.error;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

import java.util.Objects;

public class TaskDependencyException extends ApplicationException {
    public enum Reason {
        SPACES_UNAVAILABLE,
        AUTHENTICATION_UNAVAILABLE,
        INITIAL_STATUS_MISSING
    }

    private final Reason reason;

    public TaskDependencyException(Reason reason, String message) {
        super(ApiErrorCode.SERVICE_UNAVAILABLE, "Required service is unavailable");
        this.reason = Objects.requireNonNull(reason);
    }

    public Reason getReason() {
        return reason;
    }
}
