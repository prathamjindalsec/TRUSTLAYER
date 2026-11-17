package com.trustlayer.risk;

public interface RiskScoringEngine {

    RiskScoreResult score(RiskScoringRequest request);
}
