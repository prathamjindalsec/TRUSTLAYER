package com.trustlayer.dto.dashboard;

import com.trustlayer.entity.enums.RiskClassification;

/** Real aggregate count for one explainable risk classification. */
public record DashboardRiskDistributionResponse(
        RiskClassification riskClassification,
        long count
) {
}
