package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class TaskMalformedJsonException extends ApplicationException {
    public TaskMalformedJsonException() {
        super(ApiErrorCode.MALFORMED_JSON, "Malformed JSON request");
    }
}
