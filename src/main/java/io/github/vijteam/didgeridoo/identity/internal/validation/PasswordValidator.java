package io.github.vijteam.didgeridoo.identity.internal.validation;

import io.github.vijteam.didgeridoo.identity.internal.exception.PasswordValidationException;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;

public final class PasswordValidator {

    private PasswordValidator() {}

    // TODO: Рассмотреть и реализовать более сложную валидацию пароля
    public static void validate(String password) {
        if (password == null) {
            throw new PasswordValidationException(FieldErrorCode.BLANK);
        }
        if (password.length() < 8) {
            throw new PasswordValidationException(FieldErrorCode.TOO_SHORT, 8);
        }
        if (password.length() > 128) {
            throw new PasswordValidationException(FieldErrorCode.TOO_LONG, 128);
        }
    }
}
