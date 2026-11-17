package com.trustlayer.dto.transaction;

import com.trustlayer.entity.RiskIndicator;
import com.trustlayer.entity.SimulatedTransaction;
import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.TransactionAnalysisStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full result for one simulation. beneficiary is always a redacted display label. */
public record TransactionRiskResponse(
        Long transactionId,
        String simulationReference,
        BigDecimal amount,
        String currency,
        String beneficiary,
        Instant transactionTime,
        TransactionAnalysisStatus analysisStatus,
        Integer riskScore,
        RiskClassification riskClassification,
        ConfidenceLevel confidenceLevel,
        boolean simulated,
        String analyzerVersion,
        List<TransactionRiskIndicatorResponse> indicators,
        List<String> recommendations,
        List<String> limitations,
        Map<String, Object> explanation
) {

    public static TransactionRiskResponse from(SimulatedTransaction transaction, List<RiskIndicator> indicators) {
        Map<String, Object> explanation = transaction.getExplanation();
        return new TransactionRiskResponse(
                transaction.getId(),
                transaction.getExternalReference(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getBeneficiaryIdentifier(),
                transaction.getTransactionTime(),
                transaction.getAnalysisStatus(),
                transaction.getRiskScore(),
                transaction.getRiskClassification(),
                transaction.getConfidenceLevel(),
                transaction.isSimulated(),
                stringValue(explanation.get("analyzerVersion")),
                indicators.stream().map(TransactionRiskIndicatorResponse::from).toList(),
                stringList(explanation.get("recommendations")),
                stringList(explanation.get("limitations")),
                new LinkedHashMap<>(explanation)
        );
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
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
