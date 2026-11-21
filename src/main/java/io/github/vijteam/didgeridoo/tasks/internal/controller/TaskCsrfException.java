package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class TaskCsrfException extends ApplicationException {
    public TaskCsrfException() {
        super(ApiErrorCode.INVALID_CSRF_TOKEN, "CSRF token is invalid");
    }
}
