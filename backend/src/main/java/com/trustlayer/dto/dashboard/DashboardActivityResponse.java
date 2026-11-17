package com.trustlayer.dto.dashboard;

import com.trustlayer.entity.enums.RiskClassification;

import java.time.Instant;

/** A safe, user-visible timeline item backed by an existing persisted record. */
public record DashboardActivityResponse(
        Long resourceId,
        DashboardActivityType activityType,
        String title,
        String detail,
        Instant occurredAt,
        RiskClassification riskClassification,
        Integer riskScore
) {
}
