package com.trustlayer.analyzer.qr;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.risk.RiskSignal;

import java.util.List;
import java.util.Map;

/**
 * Privacy-safe QR analysis ready for persistence. It contains no raw decoded payload, payee,
 * amount, note, phone number, or URL query/path value.
 */
public record QrPayloadAnalysis(
        QrPayloadType payloadType,
        String safeTarget,
        int riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        List<RiskSignal> indicators,
        List<String> recommendations,
        List<String> limitations,
        Map<String, Object> explanation,
        String analyzerVersion,
        String imageFormat,
        int imageWidth,
        int imageHeight
) {

    public QrPayloadAnalysis {
        indicators = List.copyOf(indicators);
        recommendations = List.copyOf(recommendations);
        limitations = List.copyOf(limitations);
        explanation = Map.copyOf(explanation);
    }
}
