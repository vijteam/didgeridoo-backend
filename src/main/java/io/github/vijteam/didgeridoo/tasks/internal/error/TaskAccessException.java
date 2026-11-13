package io.github.vijteam.didgeridoo.tasks.internal.error;

import java.util.Objects;

public class TaskAccessException extends SecurityException {
    public enum Reason {
        NOT_MEMBER,
        FORBIDDEN
    }

    private final Reason reason;

    public TaskAccessException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason);
    }

    public TaskAccessException(String message) {
        this(Reason.FORBIDDEN, message);
    }

    public Reason getReason() {
        return reason;
    }
}
