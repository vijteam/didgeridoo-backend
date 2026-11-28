package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldValidationException;
import io.github.vijteam.didgeridoo.shared.web.ApiError;
import io.github.vijteam.didgeridoo.shared.web.GlobalExceptionHandler;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskConflictException;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Reuses shared application/validation handlers; adds only tasks HTTP boundary failures. */
@Order(-1)
@RestControllerAdvice(
        assignableTypes = {
            TaskReadingController.class,
            TaskWritingController.class,
            TaskClaimController.class
        })
public class TaskHttpExceptionHandler extends GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidInput(MethodArgumentTypeMismatchException failure) {
        return handleFieldValidation(
                new FieldValidationException(
                        failure.getName(),
                        FieldErrorCode.INVALID_FORMAT,
                        "Invalid request parameter"));
    }

    @ExceptionHandler(TaskConflictException.class)
    public ResponseEntity<ApiError> assignmentConflict(TaskConflictException failure) {
        return ResponseEntity.status(409)
                .body(new ApiError(ApiErrorCode.TASK_ASSIGNMENT_CONFLICT, failure.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception failure) {
        org.slf4j.LoggerFactory.getLogger(TaskHttpExceptionHandler.class)
                .error("Task HTTP request failed", failure);
        return ResponseEntity.internalServerError()
                .body(new ApiError(ApiErrorCode.INTERNAL_ERROR, "Unable to process request"));
    }
}
