package io.github.vijteam.didgeridoo.tasks.internal.error;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class TaskNotFoundException extends ApplicationException {
    public TaskNotFoundException(String message) {
        super(ApiErrorCode.NOT_FOUND, "Resource not found");
    }
}
