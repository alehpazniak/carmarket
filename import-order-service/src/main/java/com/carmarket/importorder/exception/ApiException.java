package com.carmarket.importorder.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** An error with a fixed HTTP status and a message that is safe to show to the caller. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    /** Set for 429: seconds until the caller may try again. */
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Not found");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** The same answer for every failed client check, so callers learn nothing about which one failed. */
    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid link or password");
    }

    public static ApiException storageNotConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Storage not configured");
    }
}
