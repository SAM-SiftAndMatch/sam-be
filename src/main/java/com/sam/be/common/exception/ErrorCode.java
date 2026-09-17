package com.sam.be.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(1000, "Success", HttpStatus.OK),
    VALIDATION_ERROR(1001, "Validation failed", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(1002, "Malformed JSON request", HttpStatus.BAD_REQUEST),
    FORBIDDEN_ACTION(1003, "Access denied", HttpStatus.FORBIDDEN),
    DUPLICATE_RESOURCE(1004, "Resource already exists", HttpStatus.CONFLICT),
    REQUEST_FAILED(1005, "Request failed", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1006, "Authentication required", HttpStatus.UNAUTHORIZED),
    RESOURCE_NOT_FOUND(1007, "Resource not found", HttpStatus.NOT_FOUND),
    UNSUPPORTED_MEDIA_TYPE(1008, "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    TOO_MANY_REQUESTS(1009, "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    UNEXPECTED_ERROR(9999, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    public HttpStatus getStatus() {
        return httpStatus;
    }
}
