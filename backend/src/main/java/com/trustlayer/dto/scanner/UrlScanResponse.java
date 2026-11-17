package com.trustlayer.dto.scanner;

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
 * Full URL-scan result. targetUrl is intentionally a redacted display value, not an echo of
 * untrusted user input, so secrets in URLs are not written back to clients or logs.
 */
public record UrlScanResponse(
        Long scanId,
        String targetUrl,
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

    public static UrlScanResponse from(Scan scan, ScanResult result, List<RiskIndicator> indicators) {
        Map<String, Object> explanation = result.getExplanation();
        return new UrlScanResponse(
                scan.getId(),
                scan.getTargetValue(),
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

    private static List<String> stringList(Object value) {
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
