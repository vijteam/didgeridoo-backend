package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;

/** Distinguishes syntax/duplicate-key failures without exposing input or parser details. */
public class TaskMalformedJsonException extends TaskValidationException {
    public TaskMalformedJsonException() {
        super("Malformed JSON request");
    }
}
