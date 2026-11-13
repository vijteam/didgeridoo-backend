package io.github.vijteam.didgeridoo.tasks.internal.error;

import java.util.NoSuchElementException;

public class TaskNotFoundException extends NoSuchElementException {
    public TaskNotFoundException(String message) {
        super(message);
    }
}
