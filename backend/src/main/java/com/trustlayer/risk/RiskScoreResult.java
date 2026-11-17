package com.trustlayer.risk;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, reusable decision output. It is safe to persist as structured JSON because it
 * contains only caller-provided, explainable signals and no hidden model score.
 */
public record RiskScoreResult(
        BigDecimal rawWeightedScore,
        int riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        int confirmedMaliciousIndicatorCount,
        int suspiciousIndicatorCount,
        int unknownIndicatorCount,
        List<String> indicatorCodes,
        Map<String, BigDecimal> indicatorWeights,
        List<String> limitations,
        String scoringEngineVersion
) {

    public RiskScoreResult {
        indicatorCodes = List.copyOf(indicatorCodes);
        indicatorWeights = Map.copyOf(new LinkedHashMap<>(indicatorWeights));
        limitations = List.copyOf(limitations);
    }

    public Map<String, Object> explanation() {
        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("riskScoringEngineVersion", scoringEngineVersion);
        explanation.put("scoreFormula", "min(100, round-half-up(sum of detected indicator weights))");
        explanation.put("rawWeightedScore", rawWeightedScore);
        explanation.put("riskScore", riskScore);
        explanation.put("riskClassification", riskClassification.name());
        explanation.put("confidenceLevel", confidenceLevel.name());
        explanation.put("indicatorCodes", indicatorCodes);
        explanation.put("indicatorWeights", indicatorWeights);
        explanation.put("confirmedMaliciousIndicatorCount", confirmedMaliciousIndicatorCount);
        explanation.put("suspiciousIndicatorCount", suspiciousIndicatorCount);
        explanation.put("unknownIndicatorCount", unknownIndicatorCount);
        explanation.put("limitations", limitations);
        return explanation;
    }
}
