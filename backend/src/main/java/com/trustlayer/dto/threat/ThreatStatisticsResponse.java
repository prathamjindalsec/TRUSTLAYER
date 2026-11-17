package com.trustlayer.dto.threat;

import com.trustlayer.entity.enums.ThreatCategory;

import java.util.List;
import java.util.Map;

/** Community activity counts, not a claim that every reported indicator is malicious. */
public record ThreatStatisticsResponse(
        long totalCanonicalThreats,
        long totalCommunityReports,
        long verifiedThreats,
        long unverifiedThreats,
        long rejectedThreats,
        Map<ThreatCategory, Long> threatsByCategory,
        List<ThreatResponse> repeatedCampaignCandidates,
        String limitation
) {
}
