package io.github.vijteam.didgeridoo.identity.internal.exception;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;

public class RegistrationConflictException extends ApplicationException {
    public RegistrationConflictException() {
        super(ApiErrorCode.REGISTRATION_CONFLICT, "Не удалось зарегистрироваться с указанными данными");
    }
}
