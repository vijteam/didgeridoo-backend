package io.github.vijteam.didgeridoo.spaces.api;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class SpaceAccessDeniedException extends ApplicationException {
    public SpaceAccessDeniedException() {
        super(ApiErrorCode.FORBIDDEN, "Space owner role is required");
    }
}
