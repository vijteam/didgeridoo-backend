package io.github.vijteam.didgeridoo.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.vijteam.didgeridoo.shared.ApiErrorCode;
import io.github.vijteam.didgeridoo.shared.FieldErrorCode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(ApiErrorCode code, String message, List<FieldErrorResponse> fieldErrors) {
    public record FieldErrorResponse(String field, FieldErrorCode code, String message) {}

    public ApiError(ApiErrorCode code, String message) {
        this(code, message, null);
    }
}
