package io.github.vijteam.didgeridoo.tasks.internal.error;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

import java.util.Objects;

public class TaskAccessException extends ApplicationException {
    public enum Reason {
        NOT_MEMBER,
        FORBIDDEN
    }

    private final Reason reason;

    public TaskAccessException(Reason reason, String message) {
        super(
                reason == Reason.NOT_MEMBER ? ApiErrorCode.NOT_FOUND : ApiErrorCode.FORBIDDEN,
                reason == Reason.NOT_MEMBER ? "Resource not found" : "Action is not allowed");
        this.reason = Objects.requireNonNull(reason);
    }

    public TaskAccessException(String message) {
        this(Reason.FORBIDDEN, message);
    }

    public Reason getReason() {
        return reason;
    }
}
