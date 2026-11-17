package com.trustlayer.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.threat-intelligence")
@Validated
public record ThreatIntelligenceProperties(
        @Min(1) int maxReportsPerMinute
) {
}
