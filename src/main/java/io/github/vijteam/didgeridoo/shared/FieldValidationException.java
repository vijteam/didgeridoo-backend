package io.github.vijteam.didgeridoo.shared;

import java.util.List;

public class FieldValidationException extends ApplicationException {

    public record Violation(String field, FieldErrorCode code, String message) {}

    private final List<Violation> violations;

    public FieldValidationException(String field, FieldErrorCode code, String message) {
        this(List.of(new Violation(field, code, message)));
    }

    public FieldValidationException(List<Violation> violations) {
        super(ApiErrorCode.VALIDATION_FAILED, "Invalid request data");

        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("Violations must not be empty");
        }

        this.violations = List.copyOf(violations);
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
