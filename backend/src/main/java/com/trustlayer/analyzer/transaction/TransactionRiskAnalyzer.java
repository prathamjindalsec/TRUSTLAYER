package com.trustlayer.analyzer.transaction;

import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.RiskIndicatorType;
import com.trustlayer.risk.RiskScoreResult;
import com.trustlayer.risk.RiskScoringEngine;
import com.trustlayer.risk.RiskScoringRequest;
import com.trustlayer.risk.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transparent rules for a user-entered transaction simulation. It analyzes only the requesting
 * user's persisted simulation history and never contacts, authorizes, or blocks a financial
 * institution, UPI provider, or payment rail.
 */
@Component
public class TransactionRiskAnalyzer {

    public static final String ANALYZER_VERSION = "transaction-simulation-rules-v1";

    // These nominal thresholds are a hackathon-demo policy, not currency-normalized fraud models.
    private static final BigDecimal ELEVATED_AMOUNT = new BigDecimal("25000.00");
    private static final BigDecimal HIGH_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal BASELINE_MULTIPLIER = new BigDecimal("3.00");
    private static final BigDecimal BASELINE_MINIMUM = new BigDecimal("10000.00");

    private final RiskScoringEngine riskScoringEngine;

    public TransactionRiskAnalyzer(RiskScoringEngine riskScoringEngine) {
        this.riskScoringEngine = riskScoringEngine;
    }

    public TransactionRiskAnalysis analyze(TransactionRiskInput input) {
        List<HistoricalTransaction> history = priorHistory(input.beneficiaryHistory(), input.transactionTime().toInstant());
        List<RiskSignal> signals = new ArrayList<>();
        add(signals, "SIMULATED_TRANSACTION_INPUT", "User-entered transaction simulation", RiskIndicatorType.UNKNOWN,
                IndicatorSeverity.INFO, 0, Map.of("simulated", true));

        amountSignals(input.amount(), history, signals);
        historySignals(input.amount(), history, input.transactionTime().toInstant(), signals);
        timingSignal(input.transactionTime(), signals);

        List<String> limitations = limitations();
        RiskScoreResult score = riskScoringEngine.score(new RiskScoringRequest(
                "SIMULATED_TRANSACTION_BEHAVIOR",
                signals,
                limitations,
                false
        ));
        List<String> recommendations = recommendations(score.riskClassification());
        Map<String, Object> explanation = new LinkedHashMap<>(score.explanation());
        explanation.put("analysisMode", "SIMULATED_LOCAL_TRANSACTION_BEHAVIOR");
        explanation.put("analyzerVersion", ANALYZER_VERSION);
        explanation.put("simulationOnly", true);
        explanation.put("currency", input.currency());
        explanation.put("beneficiaryHistoryCount", history.size());
        explanation.put("localTransactionHour", input.transactionTime().getHour());
        explanation.put("transactionTimeOffset", input.transactionTime().getOffset().toString());
        explanation.put("recommendations", recommendations);
        explanation.put("limitations", limitations);

        return new TransactionRiskAnalysis(
                score.riskScore(),
                score.riskClassification(),
                score.confidenceLevel(),
                signals,
                recommendations,
                limitations,
                explanation,
                ANALYZER_VERSION
        );
    }

    private void amountSignals(BigDecimal amount, List<HistoricalTransaction> history, List<RiskSignal> signals) {
        if (amount.compareTo(HIGH_AMOUNT) >= 0) {
            add(signals, "HIGH_NOMINAL_TRANSACTION_AMOUNT", "High nominal transaction amount",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.HIGH, 25,
                    Map.of("threshold", HIGH_AMOUNT));
        } else if (amount.compareTo(ELEVATED_AMOUNT) >= 0) {
            add(signals, "ELEVATED_NOMINAL_TRANSACTION_AMOUNT", "Elevated nominal transaction amount",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.MEDIUM, 12,
                    Map.of("threshold", ELEVATED_AMOUNT));
        }

        if (history.isEmpty()) {
            add(signals, "NEW_BENEFICIARY_IN_SIMULATION", "No prior completed simulation for beneficiary",
                    RiskIndicatorType.UNKNOWN, IndicatorSeverity.LOW, 4,
                    Map.of("priorCompletedSimulationCount", 0));
            if (amount.compareTo(ELEVATED_AMOUNT) >= 0) {
                add(signals, "NEW_BENEFICIARY_WITH_ELEVATED_AMOUNT",
                        "New beneficiary combined with elevated nominal amount",
                        RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.MEDIUM, 12,
                        Map.of("elevatedAmountThreshold", ELEVATED_AMOUNT));
            }
            return;
        }

        BigDecimal average = averageAmount(history);
        if (average.signum() > 0
                && amount.compareTo(BASELINE_MINIMUM) >= 0
                && amount.compareTo(average.multiply(BASELINE_MULTIPLIER)) >= 0) {
            add(signals, "AMOUNT_ABOVE_BENEFICIARY_BASELINE", "Amount is far above prior simulated beneficiary amounts",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.HIGH, 20,
                    Map.of("baselineMultiplier", BASELINE_MULTIPLIER, "priorAverageAmount", average));
        }
    }

    private void historySignals(
            BigDecimal amount,
            List<HistoricalTransaction> history,
            Instant candidateTime,
            List<RiskSignal> signals
    ) {
        if (history.isEmpty()) {
            return;
        }
        int priorHighRisk = (int) history.stream()
                .filter(transaction -> transaction.riskScore() != null && transaction.riskScore() >= 50)
                .count();
        if (priorHighRisk >= 2) {
            add(signals, "REPEATED_HIGH_RISK_SIMULATION_PATTERN",
                    "Beneficiary has repeated prior high-risk simulated analyses",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.MEDIUM, 12,
                    Map.of("priorHighRiskSimulationCount", priorHighRisk));
        }

        analyzeVelocity(amount, history, candidateTime, signals);
    }

    private void analyzeVelocity(
            BigDecimal amount,
            List<HistoricalTransaction> history,
            Instant candidateTime,
            List<RiskSignal> signals
    ) {
        long recentHour = history.stream()
                .filter(transaction -> !transaction.transactionTime().isBefore(candidateTime.minus(Duration.ofHours(1))))
                .count();
        long recentDay = history.stream()
                .filter(transaction -> !transaction.transactionTime().isBefore(candidateTime.minus(Duration.ofHours(24))))
                .count();
        if (recentHour >= 3) {
            add(signals, "BURST_OF_SIMULATED_TRANSFERS", "Multiple prior simulations occurred in a one-hour window",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.HIGH, 25,
                    Map.of("priorOneHourCount", recentHour));
        }
        if (recentDay >= 5) {
            add(signals, "HIGH_DAILY_SIMULATION_FREQUENCY", "Many prior simulations occurred in a 24-hour window",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.MEDIUM, 16,
                    Map.of("priorTwentyFourHourCount", recentDay));
        }
        BigDecimal recentHourAmount = history.stream()
                .filter(transaction -> !transaction.transactionTime().isBefore(candidateTime.minus(Duration.ofHours(1))))
                .map(HistoricalTransaction::amount)
                .reduce(amount, BigDecimal::add);
        if (recentHourAmount.compareTo(HIGH_AMOUNT) >= 0 && recentHour >= 1) {
            add(signals, "HIGH_ONE_HOUR_SIMULATED_VALUE", "One-hour simulated beneficiary value is elevated",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.MEDIUM, 12,
                    Map.of("oneHourNominalThreshold", HIGH_AMOUNT, "priorOneHourCount", recentHour));
        }
    }

    private void timingSignal(OffsetDateTime transactionTime, List<RiskSignal> signals) {
        int hour = transactionTime.getHour();
        if (hour >= 0 && hour < 5) {
            add(signals, "UNUSUAL_LOCAL_TRANSACTION_HOUR", "Transaction is simulated during overnight local hours",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.LOW, 8,
                    Map.of("localHour", hour));
        }
    }

    private List<HistoricalTransaction> priorHistory(List<HistoricalTransaction> history, Instant candidateTime) {
        return history.stream()
                .filter(transaction -> !transaction.transactionTime().isAfter(candidateTime))
                .sorted(Comparator.comparing(HistoricalTransaction::transactionTime).reversed())
                .toList();
    }

    private BigDecimal averageAmount(List<HistoricalTransaction> history) {
        BigDecimal sum = history.stream().map(HistoricalTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(history.size()), 4, RoundingMode.HALF_UP);
    }

    private List<String> limitations() {
        return List.of(
                "Simulation only: TRUSTLAYER did not connect to, authorize, block, or observe a real financial transaction.",
                "No bank, UPI, card network, beneficiary identity, device, geolocation, or payment-provider data was checked.",
                "Behavioral comparisons use only the requesting user's earlier completed demo simulations for the same hashed beneficiary.",
                "Nominal amount thresholds are demonstration rules and are not currency-normalized fraud models."
        );
    }

    private List<String> recommendations(RiskClassification classification) {
        List<String> base = new ArrayList<>();
        base.add("Verify the beneficiary independently using trusted contact details before authorizing any real payment.");
        base.add("Never share one-time passwords, PINs, card details, or remote-device access to complete a payment.");
        if (classification == RiskClassification.HIGH || classification == RiskClassification.CRITICAL) {
            base.add("Pause and seek an independent confirmation if a real request is urgent, unusual, or pressured.");
        } else if (classification == RiskClassification.MEDIUM) {
            base.add("Review the amount, beneficiary, and timing before continuing with any real payment process.");
        }
        return List.copyOf(base);
    }

    private void add(
            List<RiskSignal> signals,
            String code,
            String name,
            RiskIndicatorType type,
            IndicatorSeverity severity,
            int weight,
            Map<String, Object> evidence
    ) {
        signals.add(new RiskSignal(code, name, type, severity, BigDecimal.valueOf(weight), evidence));
    }
}
