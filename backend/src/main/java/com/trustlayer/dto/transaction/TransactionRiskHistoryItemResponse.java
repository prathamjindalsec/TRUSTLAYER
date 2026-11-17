package com.trustlayer.dto.transaction;

import com.trustlayer.entity.SimulatedTransaction;
import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.TransactionAnalysisStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Compact, owner-scoped simulation history item. */
public record TransactionRiskHistoryItemResponse(
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
        boolean simulated
) {

    public static TransactionRiskHistoryItemResponse from(SimulatedTransaction transaction) {
        return new TransactionRiskHistoryItemResponse(
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
                transaction.isSimulated()
        );
    }
}
