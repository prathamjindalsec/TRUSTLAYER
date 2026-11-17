package com.trustlayer.dto.dashboard;

import java.util.List;

/**
 * Actual database aggregates for the authenticated workspace. No metric in this response is a
 * frontend demonstration value: personal metrics are owner-scoped and community metrics are
 * explicitly platform-wide.
 */
public record DashboardStatisticsResponse(
        long totalUrlScans,
        long reviewRecommendedUrlScans,
        long totalQrScans,
        long reviewRecommendedQrScans,
        long totalSimulatedTransactions,
        long reviewRecommendedTransactions,
        long totalCommunityThreats,
        long totalCommunityReports,
        List<DashboardRiskDistributionResponse> riskDistribution,
        List<DashboardActivityResponse> recentActivity,
        String personalDataScope,
        String limitation
) {
}
