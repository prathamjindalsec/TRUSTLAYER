package com.trustlayer.dto.threat;

import com.trustlayer.entity.enums.ThreatCategory;
import com.trustlayer.entity.enums.ThreatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Unverified community input. Its value is canonicalized and redacted before persistence. */
public record ThreatReportRequest(
        @NotNull(message = "indicatorType is required.") ThreatType indicatorType,
        @NotBlank(message = "indicatorValue is required.")
        @Size(max = 2048, message = "indicatorValue must not exceed 2048 characters.")
        String indicatorValue,
        @NotNull(message = "category is required.") ThreatCategory category,
        @Size(max = 4000, message = "description must not exceed 4000 characters.")
        String description
) {
}
