package com.trustlayer.dto.scanner;

import com.trustlayer.entity.Scan;
import com.trustlayer.entity.ScanResult;
import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.ScanStatus;

import java.time.Instant;

public record UrlScanHistoryItemResponse(
        Long scanId,
        String targetUrl,
        ScanStatus status,
        Integer riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        Instant requestedAt,
        Instant completedAt
) {

    public static UrlScanHistoryItemResponse from(Scan scan, ScanResult result) {
        return new UrlScanHistoryItemResponse(
                scan.getId(),
                scan.getTargetValue(),
                scan.getStatus(),
                result == null ? null : result.getRiskScore(),
                result == null ? null : result.getRiskClassification(),
                result == null ? null : result.getConfidenceLevel(),
                scan.getRequestedAt(),
                scan.getCompletedAt()
        );
    }
}
