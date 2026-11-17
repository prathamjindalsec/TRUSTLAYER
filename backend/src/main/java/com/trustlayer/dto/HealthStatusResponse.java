package com.trustlayer.dto;

/**
 * Deliberately small health payload. It exposes no internal host, database, build, or dependency data.
 */
public record HealthStatusResponse(
        String status,
        String service
) {
}
