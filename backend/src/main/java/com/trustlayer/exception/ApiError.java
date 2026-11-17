package com.trustlayer.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        boolean success,
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
) {

    public static ApiError of(String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(false, code, message, fieldErrors, Instant.now());
    }
}
