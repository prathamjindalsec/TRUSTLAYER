package com.trustlayer.dto.scanner;

import com.trustlayer.analyzer.qr.QrPayloadType;
import com.trustlayer.entity.RiskIndicator;
import com.trustlayer.entity.Scan;
import com.trustlayer.entity.ScanResult;
import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.ScanStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full QR scan response. target is always a redacted display description unless the QR payload
 * is an HTTP(S) URL, in which case the URL scanner's existing redaction policy applies.
 */
public record QrScanResponse(
        Long scanId,
        String target,
        QrPayloadType payloadType,
        String imageFormat,
        Integer imageWidth,
        Integer imageHeight,
        ScanStatus status,
        int riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        Instant requestedAt,
        Instant completedAt,
        Instant analyzedAt,
        String analyzerVersion,
        List<UrlRiskIndicatorResponse> indicators,
        List<String> recommendations,
        List<String> limitations,
        Map<String, Object> explanation
) {

    public static QrScanResponse from(Scan scan, ScanResult result, List<RiskIndicator> indicators) {
        Map<String, Object> explanation = result.getExplanation();
        return new QrScanResponse(
                scan.getId(),
                scan.getTargetValue(),
                enumValue(explanation.get("qrPayloadType")),
                stringValue(explanation.get("qrImageFormat")),
                integerValue(explanation.get("qrImageWidth")),
                integerValue(explanation.get("qrImageHeight")),
                scan.getStatus(),
                result.getRiskScore(),
                result.getRiskClassification(),
                result.getConfidenceLevel(),
                scan.getRequestedAt(),
                scan.getCompletedAt(),
                result.getAnalyzedAt(),
                result.getAnalyzerVersion(),
                indicators.stream().map(UrlRiskIndicatorResponse::from).toList(),
                stringList(explanation.get("recommendations")),
                stringList(explanation.get("limitations")),
                new LinkedHashMap<>(explanation)
        );
    }

    static QrPayloadType enumValue(Object value) {
        try {
            return value == null ? QrPayloadType.TEXT : QrPayloadType.valueOf(value.toString());
        } catch (IllegalArgumentException exception) {
            return QrPayloadType.TEXT;
        }
    }

    static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    static Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : values) {
            if (entry != null) {
                result.add(entry.toString());
            }
        }
        return List.copyOf(result);
    }
}
