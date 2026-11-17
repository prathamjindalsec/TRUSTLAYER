package com.trustlayer.dto.threat;

import com.trustlayer.entity.enums.ThreatVerificationStatus;
import jakarta.validation.constraints.NotNull;

/** Administrator-only state transition; it must be backed by an external human verification process. */
public record ThreatVerificationRequest(
        @NotNull(message = "verificationStatus is required.") ThreatVerificationStatus verificationStatus
) {
}
