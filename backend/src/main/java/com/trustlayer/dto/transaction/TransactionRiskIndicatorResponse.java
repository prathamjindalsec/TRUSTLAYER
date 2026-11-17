package com.trustlayer.dto.transaction;

import com.trustlayer.entity.RiskIndicator;
import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskIndicatorType;

import java.math.BigDecimal;
import java.util.Map;

public record TransactionRiskIndicatorResponse(
        String code,
        String name,
        RiskIndicatorType type,
        IndicatorSeverity severity,
        BigDecimal weight,
        Map<String, Object> evidence
) {

    public static TransactionRiskIndicatorResponse from(RiskIndicator indicator) {
        return new TransactionRiskIndicatorResponse(
                indicator.getIndicatorCode(),
                indicator.getIndicatorName(),
                indicator.getIndicatorType(),
                indicator.getSeverity(),
                indicator.getWeight(),
                indicator.getEvidence()
        );
    }
}
