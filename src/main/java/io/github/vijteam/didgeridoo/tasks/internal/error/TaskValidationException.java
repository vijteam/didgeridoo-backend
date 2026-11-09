package io.github.vijteam.didgeridoo.tasks.internal.error;

public class TaskValidationException extends IllegalArgumentException {
    private final String field;

    public TaskValidationException(String message) {
        this(null, message);
    }

    public TaskValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Null when validation concerns several fields or the command itself. */
    public String getField() {
        return field;
    }
}
