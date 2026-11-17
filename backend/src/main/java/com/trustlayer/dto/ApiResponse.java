package com.trustlayer.dto;

import java.time.Instant;

/**
 * Consistent envelope for successful public API responses.
 * Error responses will be standardized when request-processing endpoints are introduced.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }
}
