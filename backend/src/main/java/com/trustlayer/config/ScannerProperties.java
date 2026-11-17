package com.trustlayer.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Deliberately small scanner configuration surface. A distributed limiter replaces this
 * per-process safeguard before the application is horizontally scaled.
 */
@ConfigurationProperties(prefix = "app.scanner")
@Validated
public record ScannerProperties(
        @Min(1) int maxSubmissionsPerMinute
) {
}
