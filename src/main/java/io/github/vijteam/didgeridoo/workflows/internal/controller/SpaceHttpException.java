package io.github.vijteam.didgeridoo.workflows.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

final class SpaceHttpException extends ApplicationException {
    SpaceHttpException(ApiErrorCode code, String message) {
        super(code, message);
    }
}
