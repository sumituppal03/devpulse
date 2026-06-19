package com.devpulse.shared.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Every error response from this API has this exact shape.
 * Consistency here matters — a client integrating against this API can
 * write ONE error-handling code path instead of guessing the shape every time.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        Map<String, String> fieldErrors ){
    public static ApiError of(int status, String error, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, fieldErrors);
    }
}
