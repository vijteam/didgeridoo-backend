package io.github.vijteam.didgeridoo.spaces.api;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class SpaceNotFoundException extends ApplicationException {
    public SpaceNotFoundException() {
        super(ApiErrorCode.NOT_FOUND, "Space not found");
    }
}
