package com.trustlayer.analyzer.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** Privacy-safe historical facts used only for simulated behavioral comparisons. */
public record HistoricalTransaction(
        BigDecimal amount,
        Instant transactionTime,
        Integer riskScore
) {
}
