package io.github.vijteam.didgeridoo.identity.internal.exception;

import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;

public class PasswordValidationException extends FieldValidationException {

    private static final String FIELD = "password";

    public PasswordValidationException(FieldErrorCode code, int limit) {
        super(FIELD, code, switch (code) {
            case TOO_LONG ->
                "Пароль должен содержать не более " + limit + " символов";
            case TOO_SHORT ->
                "Пароль должен содержать не менее " + limit + " символов";
            default ->
                throw new IllegalArgumentException("Unsupported error code: " + code);
        });
    }

    public PasswordValidationException(FieldErrorCode code) {
        super(FIELD, code, switch (code) {
            case REQUIRED ->
                "Пароль обязателен";
            case BLANK ->
                "Пароль не должен быть пустым";
            case COMPROMISED_PASSWORD ->
                "Этот пароль обнаружен в известных утечках. Выберите другой пароль";
            default ->
                throw new IllegalArgumentException("Unsupported error code: " + code);
        });
    }
}
