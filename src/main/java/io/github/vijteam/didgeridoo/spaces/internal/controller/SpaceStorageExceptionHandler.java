package io.github.vijteam.didgeridoo.spaces.internal.controller;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.web.ApiError;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(
        assignableTypes = {SpaceMembersController.class, SpaceInvitationsController.class})
public class SpaceStorageExceptionHandler {
    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> storageFailure(Exception failure) {
        org.slf4j.LoggerFactory.getLogger(SpaceStorageExceptionHandler.class)
                .error("Space operation failed", failure);
        return ResponseEntity.internalServerError()
                .body(new ApiError(ApiErrorCode.INTERNAL_ERROR, "Unable to process request"));
    }
}
