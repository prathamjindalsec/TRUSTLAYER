# TRUSTLAYER Phase 5 — centralized explainable risk scoring

## Purpose

Phase 4 had a working URL-specific score, but its classification and confidence policy lived inside the URL analyzer. That design would drift as QR, transaction, and community-intelligence modules were added.

Phase 5 centralizes the decision policy without pretending that it is machine learning. An analyzer now produces explicit evidence signals. `WeightedRiskScoringEngine` calculates the final score, score band, confidence, evidence provenance counts, and a persistable explanation.

```text
URL / QR / transaction analyzer
       |
       | List<RiskSignal>
       v
RiskScoringEngine
       |
       +-- validates signal codes and evidence provenance
       +-- sums explicit weights
       +-- caps and rounds score
       +-- assigns LOW / MEDIUM / HIGH / CRITICAL
       +-- assigns confidence
       +-- creates explainable decision metadata
       v
API response + scan_results / risk_indicators persistence
```

## Source layout

```text
backend/src/main/java/com/trustlayer/risk/
├── RiskSignal.java
├── RiskScoringRequest.java
├── RiskScoreResult.java
├── RiskScoringEngine.java
└── WeightedRiskScoringEngine.java
```

`UrlSafetyAnalyzer` now emits `RiskSignal` objects and delegates its score decision to this engine. Its existing URL rules, stored result shape, and security boundary remain intact.

## Decision contract

Each signal contains:

- a unique machine-readable code;
- a human-readable name;
- an evidence type: `CONFIRMED_MALICIOUS`, `SUSPICIOUS`, or `UNKNOWN`;
- severity;
- non-negative `BigDecimal` weight;
- structured evidence safe for persistence and API display.

The engine rejects duplicate signal codes within one decision. Duplicate codes would make the explanation ambiguous and could silently inflate a score.

The score formula is:

```text
finalScore = min(100, round-half-up(sum(signal.weight)))
```

| Final score | Classification |
| ---: | --- |
| 0–19 | `LOW` |
| 20–49 | `MEDIUM` |
| 50–79 | `HIGH` |
| 80–100 | `CRITICAL` |

Confidence is separate from risk:

- `HIGH` confidence requires at least one `CONFIRMED_MALICIOUS` signal from a verified external source and a score of 50 or greater.
- `MEDIUM` confidence requires score 30 or greater plus a high/critical-severity signal or at least three independent signals.
- Otherwise confidence is `LOW`.

An analyzer that has not queried a verified source must set `verifiedExternalEvidenceAvailable=false`. If it tries to submit `CONFIRMED_MALICIOUS` evidence anyway, the engine rejects the request. This prevents an internal rule from being falsely presented as verified fraud intelligence.

## Explainability output

The centralized engine includes these stable fields in a result explanation:

```json
{
  "riskScoringEngineVersion": "weighted-risk-engine-v1",
  "scoreFormula": "min(100, round-half-up(sum of detected indicator weights))",
  "rawWeightedScore": 54,
  "riskScore": 54,
  "riskClassification": "HIGH",
  "confidenceLevel": "MEDIUM",
  "indicatorCodes": ["HIGH_ABUSE_TLD", "BRAND_LOOKALIKE"],
  "indicatorWeights": {
    "HIGH_ABUSE_TLD": 10,
    "BRAND_LOOKALIKE": 30
  },
  "confirmedMaliciousIndicatorCount": 0,
  "suspiciousIndicatorCount": 2,
  "unknownIndicatorCount": 0,
  "limitations": ["Static local URL analysis only."]
}
```

The URL module adds its module-specific fields, such as `analysisMode`, recommendations, and privacy-redaction details, around this core output.

## Machine-learning boundary

No ML model is included in Phase 5. A model must not be introduced as an opaque score appended to the response. Before a future ML signal can affect the risk result, it needs:

1. a versioned model artifact and feature schema;
2. training and test data provenance;
3. false-positive and false-negative measurements;
4. a calibrated probability or explainable feature contribution;
5. a documented, bounded weight represented as a `RiskSignal`;
6. monitoring for drift and review of harmful bias or unsafe automation.

Until those conditions exist, calling a weighted rule score “AI” would be false.

## Tests and verification

`WeightedRiskScoringEngineTest` covers:

- exact weight retention and the 100-point cap;
- band classification and confidence calculation;
- high confidence only for verified confirmed evidence;
- rejection of unverified confirmed evidence;
- rejection of duplicate rule codes.

Phase 4 URL analyzer and HTTP integration tests continue to prove that the centralized engine preserves URL scan behavior.
