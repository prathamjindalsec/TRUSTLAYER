package com.trustlayer.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Small, explicit configuration surface for the simulation-only transaction analyzer. */
@ConfigurationProperties(prefix = "app.transactions")
@Validated
public record TransactionProperties(
        @Min(1) int maxAnalysesPerMinute
) {
}
