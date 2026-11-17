package com.trustlayer.risk;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.RiskIndicatorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightedRiskScoringEngineTest {

    private final WeightedRiskScoringEngine engine = new WeightedRiskScoringEngine();

    @Test
    void capsScoreClassifiesDecisionAndRetainsEveryWeight() {
        RiskScoreResult result = engine.score(new RiskScoringRequest(
                "TEST",
                List.of(
                        signal("DIRECT_IP", RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.HIGH, "60"),
                        signal("LOOKALIKE", RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.HIGH, "55")
                ),
                List.of("No network inspection was performed."),
                false
        ));

        assertThat(result.rawWeightedScore()).isEqualByComparingTo("115");
        assertThat(result.riskScore()).isEqualTo(100);
        assertThat(result.riskClassification()).isEqualTo(RiskClassification.CRITICAL);
        assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.indicatorWeights()).containsEntry("DIRECT_IP", new BigDecimal("60"));
        assertThat(result.explanation()).containsEntry("riskScoringEngineVersion", WeightedRiskScoringEngine.ENGINE_VERSION);
    }

    @Test
    void verifiedConfirmedEvidenceCanReachHighConfidence() {
        RiskScoreResult result = engine.score(new RiskScoringRequest(
                "TEST",
                List.of(signal("THREAT_FEED_MATCH", RiskIndicatorType.CONFIRMED_MALICIOUS, IndicatorSeverity.CRITICAL, "80")),
                List.of("Evidence came from a verified source."),
                true
        ));

        assertThat(result.riskClassification()).isEqualTo(RiskClassification.CRITICAL);
        assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.confirmedMaliciousIndicatorCount()).isEqualTo(1);
    }

    @Test
    void refusesUnverifiedConfirmedEvidenceAndDuplicateCodes() {
        assertThatThrownBy(() -> engine.score(new RiskScoringRequest(
                "TEST",
                List.of(signal("UNVERIFIED", RiskIndicatorType.CONFIRMED_MALICIOUS, IndicatorSeverity.HIGH, "80")),
                List.of(),
                false
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verified external evidence");

        assertThatThrownBy(() -> engine.score(new RiskScoringRequest(
                "TEST",
                List.of(
                        signal("DUPLICATE", RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.LOW, "10"),
                        signal("DUPLICATE", RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.LOW, "10")
                ),
                List.of(),
                false
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    private RiskSignal signal(String code, RiskIndicatorType type, IndicatorSeverity severity, String weight) {
        return new RiskSignal(code, code, type, severity, new BigDecimal(weight), Map.of("test", true));
    }
}
