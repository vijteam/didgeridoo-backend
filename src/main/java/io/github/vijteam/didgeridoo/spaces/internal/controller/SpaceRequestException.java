package io.github.vijteam.didgeridoo.spaces.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

final class SpaceRequestException extends ApplicationException {
    SpaceRequestException(ApiErrorCode code, String message) {
        super(code, message);
    }
}
