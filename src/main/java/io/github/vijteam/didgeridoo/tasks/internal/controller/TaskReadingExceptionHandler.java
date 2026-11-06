package io.github.vijteam.didgeridoo.tasks.internal.controller;

import io.github.vijteam.didgeridoo.tasks.internal.dto.TaskApiError;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskAccessException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskDependencyException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskNotFoundException;
import io.github.vijteam.didgeridoo.tasks.internal.error.TaskValidationException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Applies only to the tasks reading controller; does not configure shared infrastructure. */
@RestControllerAdvice(assignableTypes = TaskReadingController.class)
public class TaskReadingExceptionHandler {
    @ExceptionHandler(TaskAuthenticationException.class)
    public ResponseEntity<TaskApiError> unauthenticated() {
        return error(401, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler(TaskAccessException.class)
    public ResponseEntity<TaskApiError> access(TaskAccessException failure) {
        return failure.getReason() == TaskAccessException.Reason.NOT_MEMBER
                ? notFound()
                : error(403, "FORBIDDEN", "Action is not allowed");
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<TaskApiError> notFound() {
        return error(404, "RESOURCE_NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler(TaskDependencyException.class)
    public ResponseEntity<TaskApiError> dependency() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Required service is unavailable");
    }

    @ExceptionHandler({TaskValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<TaskApiError> invalidInput() {
        return error(400, "VALIDATION_FAILED", "Invalid request data");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TaskApiError> unexpected(Exception failure) {
        org.slf4j.LoggerFactory.getLogger(TaskReadingExceptionHandler.class)
                .error("Task reading request failed", failure);
        return error(500, "INTERNAL_ERROR", "Unable to process request");
    }

    private ResponseEntity<TaskApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new TaskApiError(code, message));
    }
}
