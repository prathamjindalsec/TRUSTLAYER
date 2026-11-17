package com.trustlayer.dto.threat;

import com.trustlayer.entity.enums.ThreatReportStatus;

import java.time.Instant;

public record ThreatReportResponse(
        Long reportId,
        ThreatReportStatus reportStatus,
        boolean createdNewThreat,
        boolean corroboratesExistingThreat,
        Instant submittedAt,
        ThreatResponse threat
) {
}
