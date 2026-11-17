package com.trustlayer.analyzer.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * In-memory input to the deterministic transaction simulator. It intentionally has no raw
 * beneficiary identifier; the service hashes and redacts that value before analysis begins.
 */
public record TransactionRiskInput(
        BigDecimal amount,
        String currency,
        OffsetDateTime transactionTime,
        List<HistoricalTransaction> beneficiaryHistory
) {

    public TransactionRiskInput {
        beneficiaryHistory = beneficiaryHistory == null ? List.of() : List.copyOf(beneficiaryHistory);
    }
}
