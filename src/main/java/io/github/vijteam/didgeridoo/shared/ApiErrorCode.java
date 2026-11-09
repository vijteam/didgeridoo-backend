package io.github.vijteam.didgeridoo.shared;

public enum ApiErrorCode {

    // 400 Bad Request
    VALIDATION_FAILED,
    MALFORMED_JSON,

    // 401 Unauthorized
    INVALID_CREDENTIALS,
    UNAUTHENTICATED,

    // 403 Forbidden
    INVALID_CSRF_TOKEN,
    INVALID_CURRENT_PASSWORD,
    FORBIDDEN,

    // 404 Not Found
    NOT_FOUND,

    // 409 Conflict
    REGISTRATION_CONFLICT,
    USERNAME_UNAVAILABLE,

    // 413 Payload Too Large
    PAYLOAD_TOO_LARGE,

    // 415 Unsupported Media Type
    UNSUPPORTED_MEDIA_TYPE,

    // 500 Internal Server Error
    INTERNAL_ERROR,

    // 503 Service Unavailable
    SERVICE_UNAVAILABLE,
    MEDIA_STORAGE_UNAVAILABLE
}
