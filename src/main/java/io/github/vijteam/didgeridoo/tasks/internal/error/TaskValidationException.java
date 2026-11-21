package io.github.vijteam.didgeridoo.tasks.internal.error;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

/** Request-wide validation failure without a fictional field violation. */
public class TaskValidationException extends ApplicationException {
    public TaskValidationException(String message) {
        super(ApiErrorCode.VALIDATION_FAILED, message);
    }
}
