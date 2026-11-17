package com.trustlayer.dto.threat;

import com.trustlayer.entity.Threat;
import com.trustlayer.entity.enums.ThreatCategory;
import com.trustlayer.entity.enums.ThreatType;
import com.trustlayer.entity.enums.ThreatVerificationStatus;

import java.time.Instant;

/** Safe public representation: indicator is a redacted display value where necessary. */
public record ThreatResponse(
        Long threatId,
        ThreatType indicatorType,
        String indicator,
        ThreatCategory category,
        ThreatVerificationStatus verificationStatus,
        Integer confidenceScore,
        int communityReportCount,
        boolean repeatedCampaignCandidate,
        Instant firstReportedAt,
        Instant lastReportedAt,
        Instant verifiedAt,
        String verificationNotice
) {

    public static ThreatResponse from(Threat threat) {
        return new ThreatResponse(
                threat.getId(),
                threat.getThreatType(),
                threat.getIndicatorValue(),
                threat.getCategory(),
                threat.getVerificationStatus(),
                threat.getConfidenceScore(),
                threat.getReportCount(),
                threat.getReportCount() >= 3,
                threat.getFirstReportedAt(),
                threat.getLastReportedAt(),
                threat.getVerifiedAt(),
                verificationNotice(threat.getVerificationStatus())
        );
    }

    private static String verificationNotice(ThreatVerificationStatus status) {
        return switch (status) {
            case VERIFIED -> "This indicator was marked verified by an authorized TRUSTLAYER reviewer.";
            case REJECTED -> "This indicator was rejected by an authorized TRUSTLAYER reviewer; it is not a confirmed threat.";
            case UNVERIFIED -> "Community reports are unverified submissions and do not prove fraud or maliciousness.";
        };
    }
}
