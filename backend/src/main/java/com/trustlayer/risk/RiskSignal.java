package com.trustlayer.risk;

import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskIndicatorType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One independently explainable input to the shared risk engine. An analyzer creates signals;
 * it does not calculate classifications or confidence itself.
 */
public record RiskSignal(
        String code,
        String name,
        RiskIndicatorType type,
        IndicatorSeverity severity,
        BigDecimal weight,
        Map<String, Object> evidence
) {

    public RiskSignal {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Risk signals require a code and name.");
        }
        if (type == null || severity == null || weight == null || weight.signum() < 0) {
            throw new IllegalArgumentException("Risk signals require non-negative weighted evidence.");
        }
        evidence = evidence == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(evidence));
    }
}
