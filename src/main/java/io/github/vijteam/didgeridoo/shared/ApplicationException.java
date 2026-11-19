package io.github.vijteam.didgeridoo.shared;

public abstract class ApplicationException extends RuntimeException {

    private final ApiErrorCode code;

    protected ApplicationException(ApiErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiErrorCode getCode() {
        return code;
    }
}
