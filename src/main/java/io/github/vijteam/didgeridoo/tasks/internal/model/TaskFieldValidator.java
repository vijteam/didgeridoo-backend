package io.github.vijteam.didgeridoo.tasks.internal.model;

import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;

final class TaskFieldValidator {
    private TaskFieldValidator() {}

    static String text(String value, int maxLength, String field) {
        if (value == null
                || value.isBlank()
                || value.codePointCount(0, value.length()) > maxLength) {
            throw new TaskValidationException(field, "Invalid " + field);
        }
        return value;
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new TaskValidationException(field, field + " is required");
        }
        return value;
    }
}
