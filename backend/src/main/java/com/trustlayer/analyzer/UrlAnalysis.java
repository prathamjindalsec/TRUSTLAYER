package com.trustlayer.analyzer;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.risk.RiskSignal;

import java.util.List;
import java.util.Map;

/**
 * In-memory output of the static URL analyzer. It contains no fetched content and no claim that
 * a destination is benign. Persistence is handled by the scanner service.
 */
public record UrlAnalysis(
        String targetUrl,
        int riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        List<RiskSignal> indicators,
        List<String> recommendations,
        List<String> limitations,
        Map<String, Object> explanation,
        String analyzerVersion
) {

    public UrlAnalysis {
        indicators = List.copyOf(indicators);
        recommendations = List.copyOf(recommendations);
        limitations = List.copyOf(limitations);
        explanation = Map.copyOf(explanation);
    }

}
