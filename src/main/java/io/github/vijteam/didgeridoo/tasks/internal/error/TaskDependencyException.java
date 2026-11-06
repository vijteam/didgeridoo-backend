package io.github.vijteam.didgeridoo.tasks.internal.error;

import java.util.Objects;

public class TaskDependencyException extends IllegalStateException {
    public enum Reason {
        SPACES_UNAVAILABLE,
        AUTHENTICATION_UNAVAILABLE,
        INITIAL_STATUS_MISSING
    }

    private final Reason reason;

    public TaskDependencyException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason);
    }

    public Reason getReason() {
        return reason;
    }
}
