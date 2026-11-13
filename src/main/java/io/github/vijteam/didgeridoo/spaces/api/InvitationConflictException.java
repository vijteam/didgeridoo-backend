package io.github.vijteam.didgeridoo.spaces.api;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class InvitationConflictException extends ApplicationException {
    public InvitationConflictException(String message) {
        super(ApiErrorCode.INVITATION_CONFLICT, message);
    }
}
