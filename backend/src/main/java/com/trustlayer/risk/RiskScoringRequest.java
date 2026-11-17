package com.trustlayer.risk;

import java.util.List;

/**
 * A context-aware request to the centralized scoring policy. confirmed-malicious evidence is
 * rejected unless the caller explicitly proves it came from a verified source.
 */
public record RiskScoringRequest(
        String analysisContext,
        List<RiskSignal> signals,
        List<String> limitations,
        boolean verifiedExternalEvidenceAvailable
) {

    public RiskScoringRequest {
        if (analysisContext == null || analysisContext.isBlank()) {
            throw new IllegalArgumentException("Risk scoring requires an analysis context.");
        }
        signals = signals == null ? List.of() : List.copyOf(signals);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
