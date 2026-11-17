package com.trustlayer.dto.scanner;

import com.trustlayer.entity.RiskIndicator;
import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskIndicatorType;

import java.math.BigDecimal;
import java.util.Map;

public record UrlRiskIndicatorResponse(
        String code,
        String name,
        RiskIndicatorType type,
        IndicatorSeverity severity,
        BigDecimal weight,
        Map<String, Object> evidence
) {

    public static UrlRiskIndicatorResponse from(RiskIndicator indicator) {
        return new UrlRiskIndicatorResponse(
                indicator.getIndicatorCode(),
                indicator.getIndicatorName(),
                indicator.getIndicatorType(),
                indicator.getSeverity(),
                indicator.getWeight(),
                indicator.getEvidence()
        );
    }
}
