package com.trustlayer.dto.scanner;

import com.trustlayer.analyzer.qr.QrPayloadType;
import com.trustlayer.entity.Scan;
import com.trustlayer.entity.ScanResult;
import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.ScanStatus;

import java.time.Instant;

/** Compact, owner-scoped QR history item that never exposes the decoded payload. */
public record QrScanHistoryItemResponse(
        Long scanId,
        String target,
        QrPayloadType payloadType,
        ScanStatus status,
        Integer riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        Instant requestedAt,
        Instant completedAt
) {

    public static QrScanHistoryItemResponse from(Scan scan, ScanResult result) {
        return new QrScanHistoryItemResponse(
                scan.getId(),
                scan.getTargetValue(),
                result == null ? QrPayloadType.TEXT : QrScanResponse.enumValue(result.getExplanation().get("qrPayloadType")),
                scan.getStatus(),
                result == null ? null : result.getRiskScore(),
                result == null ? null : result.getRiskClassification(),
                result == null ? null : result.getConfidenceLevel(),
                scan.getRequestedAt(),
                scan.getCompletedAt()
        );
    }
}
