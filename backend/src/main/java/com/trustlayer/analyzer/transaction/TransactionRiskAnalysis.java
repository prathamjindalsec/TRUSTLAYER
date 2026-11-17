package com.trustlayer.analyzer.transaction;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.risk.RiskSignal;

import java.util.List;
import java.util.Map;

/** Deterministic simulated-transaction result ready for persistence and API display. */
public record TransactionRiskAnalysis(
        int riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        List<RiskSignal> indicators,
        List<String> recommendations,
        List<String> limitations,
        Map<String, Object> explanation,
        String analyzerVersion
) {

    public TransactionRiskAnalysis {
        indicators = List.copyOf(indicators);
        recommendations = List.copyOf(recommendations);
        limitations = List.copyOf(limitations);
        explanation = Map.copyOf(explanation);
    }
}
