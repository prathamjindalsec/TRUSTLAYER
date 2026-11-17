# TRUSTLAYER Phase 7 — simulated transaction-risk analysis

## Purpose and non-negotiable boundary

Phase 7 implements an authenticated, explainable analyzer for **user-entered simulated transactions**. It is a hackathon demonstration tool that compares a scenario against the same user's earlier simulated history for the same one-way-hashed beneficiary identifier.

It does **not** connect to a bank, UPI provider, wallet, card network, merchant, beneficiary, account, device, geolocation source, or payment rail. It cannot see, initiate, approve, delay, decline, reverse, or block a real transaction. Calling this a real-time banking-control system would be false.

The API response always states `simulated: true` and includes this limitation explicitly.

## Architecture

```text
Authenticated client + XSRF proof
        |
        | amount, currency, beneficiary identifier, offset-aware transaction time
        v
TransactionController
        |
        v
TransactionRiskService
        |-- validates and normalizes input
        |-- SHA-256 hashes normalized beneficiary input
        |-- retains only a redacted beneficiary display label
        |-- loads prior completed simulations for the same user + beneficiary hash
        v
TransactionRiskAnalyzer (deterministic local rules)
        |
        v
WeightedRiskScoringEngine
        |
        +--> transactions -> risk_indicators
        +--> audit_logs (TRANSACTION.SIMULATION_ANALYZED)
        v
Redacted, owner-scoped JSON response
```

The service does not persist the raw `beneficiaryIdentifier`. `transactions.beneficiary_identifier` receives the fixed label `Beneficiary identifier (redacted)` and `beneficiary_hash` receives a SHA-256 hash of its normalized value. That allows the same user's demonstration history to be grouped without making the source identifier available through normal application storage or responses.

## Transparent rule policy

Every rule goes through the shared `WeightedRiskScoringEngine`; there is no opaque AI score and no `CONFIRMED_MALICIOUS` indicator in this phase.

The following values are intentionally **nominal demonstration thresholds**, not currency-normalized fraud policy. `25,000` in one currency cannot be meaningfully compared with `25,000` in another. A real product requires currency conversion, calibrated data, policy approval, and false-positive/false-negative evaluation.

| Rule code | Condition | Type | Weight |
| --- | --- | --- | ---: |
| `SIMULATED_TRANSACTION_INPUT` | Every request | `UNKNOWN` | 0 |
| `ELEVATED_NOMINAL_TRANSACTION_AMOUNT` | Amount >= 25,000 and < 100,000 | `SUSPICIOUS` | 12 |
| `HIGH_NOMINAL_TRANSACTION_AMOUNT` | Amount >= 100,000 | `SUSPICIOUS` | 25 |
| `NEW_BENEFICIARY_IN_SIMULATION` | No earlier completed simulation for this user/hashed beneficiary | `UNKNOWN` | 4 |
| `NEW_BENEFICIARY_WITH_ELEVATED_AMOUNT` | New beneficiary and amount >= 25,000 | `SUSPICIOUS` | 12 |
| `AMOUNT_ABOVE_BENEFICIARY_BASELINE` | Amount >= 10,000 and at least 3× the prior average | `SUSPICIOUS` | 20 |
| `BURST_OF_SIMULATED_TRANSFERS` | At least 3 earlier simulations in the preceding hour | `SUSPICIOUS` | 25 |
| `HIGH_DAILY_SIMULATION_FREQUENCY` | At least 5 earlier simulations in the preceding 24 hours | `SUSPICIOUS` | 16 |
| `HIGH_ONE_HOUR_SIMULATED_VALUE` | Current plus prior one-hour simulated value >= 100,000 | `SUSPICIOUS` | 12 |
| `REPEATED_HIGH_RISK_SIMULATION_PATTERN` | At least 2 earlier same-beneficiary simulations scored 50+ | `SUSPICIOUS` | 12 |
| `UNUSUAL_LOCAL_TRANSACTION_HOUR` | Supplied offset-aware local time is 00:00–04:59 | `SUSPICIOUS` | 8 |

The time offset supplied in `transactionTime` is preserved while the timing rule runs. The stored timestamp is normalized to UTC, while the explanation records the original offset and local hour used for the decision.

Scores are capped at 100: `LOW` 0–19, `MEDIUM` 20–49, `HIGH` 50–79, and `CRITICAL` 80–100. `HIGH` confidence is impossible in this phase because there is no verified external malicious evidence.

## Database change

`V3__add_transaction_confidence_level.sql` adds a nullable `transactions.confidence_level` field. This is an additive immutable Flyway migration; it does not alter V1 or V2. The `transactions` and transaction branch of `risk_indicators` already existed in the Phase 2 schema.

## REST API

All routes require a valid bearer access token. `POST` also requires the matching CSRF cookie/header pair.

### POST `/api/transactions/analyze`

Request:

```http
Authorization: Bearer <access-token>
X-XSRF-TOKEN: <csrf-token>
Cookie: XSRF-TOKEN=<csrf-token>
Content-Type: application/json
```

```json
{
  "amount": 120000.00,
  "currency": "INR",
  "beneficiaryIdentifier": "demo-beneficiary@bank",
  "transactionTime": "2026-11-16T02:30:00+05:30"
}
```

`transactionTime` must include an ISO-8601 offset and must not be in the future. The raw beneficiary value is accepted only for in-memory hash calculation and is never echoed in a result.

Success: HTTP `201 Created`.

```json
{
  "success": true,
  "message": "Simulated transaction risk analysis completed; no real transaction was initiated or blocked.",
  "data": {
    "transactionId": 51,
    "simulationReference": "server-generated UUID",
    "amount": 120000.00,
    "currency": "INR",
    "beneficiary": "Beneficiary identifier (redacted)",
    "analysisStatus": "COMPLETED",
    "riskScore": 53,
    "riskClassification": "HIGH",
    "confidenceLevel": "MEDIUM",
    "simulated": true,
    "analyzerVersion": "transaction-simulation-rules-v1",
    "explanation": {
      "simulationOnly": true,
      "localTransactionHour": 2,
      "transactionTimeOffset": "+05:30",
      "confirmedMaliciousIndicatorCount": 0
    }
  }
}
```

| Status | Code | Cause |
| ---: | --- | --- |
| 400 | `VALIDATION_FAILED` | Missing/invalid amount, currency, beneficiary identifier, or time; future time. |
| 400 | `INVALID_BENEFICIARY_IDENTIFIER` | Identifier contains unsupported control characters after normalization. |
| 401 | `AUTHENTICATION_REQUIRED` | No valid active access token. |
| 403 | `ACCESS_DENIED` | Missing/invalid CSRF proof. |
| 429 | `TRANSACTION_ANALYSIS_RATE_LIMITED` | More than the configured per-user simulation allowance in a minute. |

### GET `/api/transactions/history?page=0&size=20`

Returns only the caller's simulated transaction analyses, newest first. It contains a redacted beneficiary label and no raw identifier.

### GET `/api/transactions/{transactionId}`

Returns the caller's full result. Another user's ID deliberately returns `404 TRANSACTION_NOT_FOUND` to prevent IDOR disclosure.

## Configuration

| Variable | Default | Meaning |
| --- | ---: | --- |
| `TRUSTLAYER_TRANSACTION_MAX_ANALYSES_PER_MINUTE` | 20 | Per-user simulation-analysis limit per minute, per application instance. |

As with the existing scanner throttle, this is an in-memory single-instance control. It must be replaced with a shared limiter before horizontal scale.

## Automated verification

The tests cover:

- deterministic scoring of high amount, historical baseline, burst, repeated-high-risk, and overnight signals;
- exclusion of future historical records from the candidate's behavioral comparison;
- full authenticated API flow with CSRF enforcement;
- safe beneficiary redaction in create, history, and detail responses;
- correct preservation of the input time offset for the overnight rule;
- owner-only history/detail access and `404` for another user's ID;
- future time rejection.

Run the standard suite:

```zsh
cd /Users/prathamjindal/Documents/Codex/2026-11-16/trustlayer-ultimate-master-development-prompt-ai/backend
./mvnw -ntp test
```

Run MySQL/Flyway validation after the V3 migration:

```zsh
TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw -ntp verify
```

## Known limitations

- Amount policy is not currency-normalized, calibrated, or suitable for a financial decision.
- The system knows only past simulations created inside this account; it has no real account, beneficiary, device, or payment behavior.
- No rule proves fraud or safety. A high score is a review prompt, not an automatic payment decision.
- This is not an anti-money-laundering, banking, credit, sanctions, KYC, or transaction-blocking system.
