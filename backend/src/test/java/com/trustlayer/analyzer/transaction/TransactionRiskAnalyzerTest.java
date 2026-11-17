package com.trustlayer.analyzer.transaction;

import com.trustlayer.risk.WeightedRiskScoringEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRiskAnalyzerTest {

    private final TransactionRiskAnalyzer analyzer = new TransactionRiskAnalyzer(new WeightedRiskScoringEngine());

    @Test
    void createsRepeatableBehavioralSignalsWithoutClaimingConfirmedFraud() {
        OffsetDateTime candidate = OffsetDateTime.of(2026, 11, 16, 2, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<HistoricalTransaction> history = List.of(
                new HistoricalTransaction(new BigDecimal("1000"), candidate.minusMinutes(10).toInstant(), 70),
                new HistoricalTransaction(new BigDecimal("1200"), candidate.minusMinutes(20).toInstant(), 60),
                new HistoricalTransaction(new BigDecimal("900"), candidate.minusMinutes(30).toInstant(), 20)
        );

        TransactionRiskAnalysis result = analyzer.analyze(new TransactionRiskInput(
                new BigDecimal("120000"), "INR", candidate, history
        ));

        assertThat(result.riskScore()).isEqualTo(100);
        assertThat(result.indicators()).extracting(signal -> signal.code()).contains(
                "HIGH_NOMINAL_TRANSACTION_AMOUNT",
                "AMOUNT_ABOVE_BENEFICIARY_BASELINE",
                "BURST_OF_SIMULATED_TRANSFERS",
                "REPEATED_HIGH_RISK_SIMULATION_PATTERN",
                "UNUSUAL_LOCAL_TRANSACTION_HOUR"
        );
        assertThat(result.explanation().get("confirmedMaliciousIndicatorCount")).isEqualTo(0);
        assertThat(result.limitations()).anyMatch(value -> value.contains("Simulation only"));
    }

    @Test
    void ignoresFutureHistoryWhenCalculatingTheCandidate() {
        OffsetDateTime candidate = OffsetDateTime.of(2026, 11, 16, 12, 0, 0, 0, ZoneOffset.UTC);
        TransactionRiskAnalysis result = analyzer.analyze(new TransactionRiskInput(
                new BigDecimal("1000"),
                "INR",
                candidate,
                List.of(new HistoricalTransaction(new BigDecimal("999999"), Instant.parse("2026-11-17T12:00:00Z"), 100))
        ));

        assertThat(result.explanation().get("beneficiaryHistoryCount")).isEqualTo(0);
        assertThat(result.indicators()).extracting(signal -> signal.code()).contains("NEW_BENEFICIARY_IN_SIMULATION");
    }
}
