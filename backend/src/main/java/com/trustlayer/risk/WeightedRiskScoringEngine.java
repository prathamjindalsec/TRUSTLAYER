package com.trustlayer.risk;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.RiskIndicatorType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Transparent weighted policy shared by all analysis modules. Rule producers own their evidence
 * and weights; this engine owns score capping, bands, provenance counts, and confidence rules.
 */
@Component
public class WeightedRiskScoringEngine implements RiskScoringEngine {

    public static final String ENGINE_VERSION = "weighted-risk-engine-v1";
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    @Override
    public RiskScoreResult score(RiskScoringRequest request) {
        validateProvenanceAndCodes(request);

        BigDecimal rawWeightedScore = request.signals().stream()
                .map(RiskSignal::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int score = normalizedScore(rawWeightedScore);

        int confirmed = count(request, RiskIndicatorType.CONFIRMED_MALICIOUS);
        int suspicious = count(request, RiskIndicatorType.SUSPICIOUS);
        int unknown = count(request, RiskIndicatorType.UNKNOWN);
        RiskClassification classification = classification(score);
        ConfidenceLevel confidence = confidence(request, score, confirmed);

        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        request.signals().forEach(signal -> weights.put(signal.code(), signal.weight()));

        return new RiskScoreResult(
                rawWeightedScore,
                score,
                classification,
                confidence,
                confirmed,
                suspicious,
                unknown,
                request.signals().stream().map(RiskSignal::code).toList(),
                weights,
                request.limitations(),
                ENGINE_VERSION
        );
    }

    private void validateProvenanceAndCodes(RiskScoringRequest request) {
        Set<String> codes = new LinkedHashSet<>();
        for (RiskSignal signal : request.signals()) {
            if (!codes.add(signal.code())) {
                throw new IllegalArgumentException("Risk signal codes must be unique per decision.");
            }
            if (signal.type() == RiskIndicatorType.CONFIRMED_MALICIOUS
                    && !request.verifiedExternalEvidenceAvailable()) {
                throw new IllegalArgumentException(
                        "Confirmed-malicious evidence requires a verified external evidence source."
                );
            }
        }
    }

    private int normalizedScore(BigDecimal rawWeightedScore) {
        if (rawWeightedScore.compareTo(MAX_SCORE) >= 0) {
            return 100;
        }
        return rawWeightedScore.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private int count(RiskScoringRequest request, RiskIndicatorType type) {
        return (int) request.signals().stream().filter(signal -> signal.type() == type).count();
    }

    private RiskClassification classification(int score) {
        if (score >= 80) {
            return RiskClassification.CRITICAL;
        }
        if (score >= 50) {
            return RiskClassification.HIGH;
        }
        if (score >= 20) {
            return RiskClassification.MEDIUM;
        }
        return RiskClassification.LOW;
    }

    private ConfidenceLevel confidence(RiskScoringRequest request, int score, int confirmedCount) {
        if (request.verifiedExternalEvidenceAvailable() && confirmedCount > 0 && score >= 50) {
            return ConfidenceLevel.HIGH;
        }

        boolean highSeveritySignal = request.signals().stream()
                .anyMatch(signal -> signal.severity() == IndicatorSeverity.HIGH
                        || signal.severity() == IndicatorSeverity.CRITICAL);
        if (score >= 30 && (highSeveritySignal || request.signals().size() >= 3)) {
            return ConfidenceLevel.MEDIUM;
        }
        return ConfidenceLevel.LOW;
    }
}
