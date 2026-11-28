package io.github.vijteam.didgeridoo.shared.web;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.ApplicationException;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiError> handleApplication(ApplicationException ex) {
        ApiError response = new ApiError(ex.getCode(), ex.getMessage());

        return ResponseEntity.status(mapHttpStatus(ex.getCode())).body(response);
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiError> handleFieldValidation(FieldValidationException ex) {
        List<ApiError.FieldErrorResponse> fieldErrors =
                ex.getViolations().stream()
                        .map(v -> new ApiError.FieldErrorResponse(v.field(), v.code(), v.message()))
                        .toList();

        ApiError response = new ApiError(ex.getCode(), ex.getMessage(), fieldErrors);

        return ResponseEntity.status(mapHttpStatus(ex.getCode())).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        List<ApiError.FieldErrorResponse> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new ApiError.FieldErrorResponse(
                                                error.getField(),
                                                mapValidationCode(error),
                                                error.getDefaultMessage()))
                        .toList();

        return validationError(fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        ApiError response = new ApiError(ApiErrorCode.MALFORMED_JSON, "Invalid request body", null);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType() {
        return ResponseEntity.status(415)
                .body(
                        new ApiError(
                                ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                                "JSON content type is required"));
    }

    private ResponseEntity<ApiError> validationError(
            List<ApiError.FieldErrorResponse> fieldErrors) {
        ApiError response =
                new ApiError(ApiErrorCode.VALIDATION_FAILED, "Invalid request data", fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    private FieldErrorCode mapValidationCode(FieldError error) {
        String code = error.getCode();

        if (error.isBindingFailure()) {
            return FieldErrorCode.INVALID_TYPE;
        }

        if ("NotNull".equals(code) || "NotEmpty".equals(code)) {
            return FieldErrorCode.REQUIRED;
        }

        if ("NotBlank".equals(code)) {
            return error.getRejectedValue() == null
                    ? FieldErrorCode.REQUIRED
                    : FieldErrorCode.BLANK;
        }

        if ("Email".equals(code) || "Pattern".equals(code)) {
            return FieldErrorCode.INVALID_FORMAT;
        }

        if ("Size".equals(code)) {
            return mapSizeCode(error);
        }

        throw new IllegalStateException("Unmapped validation constraint: " + code);
    }

    private FieldErrorCode mapSizeCode(FieldError error) {
        Object[] arguments = error.getArguments();

        if (arguments == null
                || arguments.length < 3
                || !(arguments[1] instanceof Integer max)
                || !(arguments[2] instanceof Integer min)
                || !(error.getRejectedValue() instanceof String value)) {
            throw new IllegalStateException("Cannot map Size constraint for: " + error.getField());
        }

        if (value.length() < min) {
            return FieldErrorCode.TOO_SHORT;
        }

        if (value.length() > max) {
            return FieldErrorCode.TOO_LONG;
        }

        throw new IllegalStateException("Unexpected Size violation: " + error.getField());
    }

    private HttpStatus mapHttpStatus(ApiErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED, MALFORMED_JSON -> HttpStatus.BAD_REQUEST;
            case INVALID_CREDENTIALS, UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case INVALID_CSRF_TOKEN, INVALID_CURRENT_PASSWORD, FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVITATION_CONFLICT,
                    IDEMPOTENCY_CONFLICT,
                    REGISTRATION_CONFLICT,
                    USERNAME_UNAVAILABLE ->
                    HttpStatus.CONFLICT;
            case PAYLOAD_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SERVICE_UNAVAILABLE, MEDIA_STORAGE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
