package io.github.vijteam.didgeridoo.tasks.internal.error;

public class TaskConflictException extends IllegalStateException {
    public TaskConflictException(String message) {
        super(message);
    }
}
