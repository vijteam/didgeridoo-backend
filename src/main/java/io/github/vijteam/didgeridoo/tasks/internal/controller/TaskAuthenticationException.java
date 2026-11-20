package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class TaskAuthenticationException extends ApplicationException {
    public TaskAuthenticationException() {
        super(ApiErrorCode.UNAUTHENTICATED, "Authentication required");
    }
}
