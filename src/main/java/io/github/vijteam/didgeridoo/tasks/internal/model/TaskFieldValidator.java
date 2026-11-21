package io.github.vijteam.didgeridoo.tasks.internal.model;

import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;

final class TaskFieldValidator {
    private TaskFieldValidator() {}

    static String text(String value, int maxLength, String field) {
        if (value == null) {
            throw new FieldValidationException(field, FieldErrorCode.REQUIRED, "Field is required");
        }
        if (value.isBlank()) {
            throw new FieldValidationException(
                    field, FieldErrorCode.BLANK, "Field must not be blank");
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new FieldValidationException(field, FieldErrorCode.TOO_LONG, "Field is too long");
        }
        return value;
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new FieldValidationException(field, FieldErrorCode.REQUIRED, "Field is required");
        }
        return value;
    }
}
