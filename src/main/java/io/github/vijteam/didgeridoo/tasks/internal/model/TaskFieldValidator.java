package io.github.vijteam.didgeridoo.tasks.internal.model;

final class TaskFieldValidator {
    private TaskFieldValidator() {}

    static String text(String value, int maxLength, String field) {
        if (value == null
                || value.isBlank()
                || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
