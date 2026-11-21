package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class TaskMediaTypeException extends ApplicationException {
    public TaskMediaTypeException() {
        super(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "JSON content type is required");
    }
}
