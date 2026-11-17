# TRUSTLAYER Phase 8 — community threat intelligence

## Purpose and truth boundary

Phase 8 accepts authenticated community reports, provides safe search, and derives database-backed
frequency statistics. A submission is **not** a fraud verdict. A repeated report is corroborating
community input, not proof that an indicator is malicious.

The application does not fetch submitted URLs, look up DNS or reputation, initiate payments, or
take down destinations. Reporting is deliberately decoupled from external network activity.

```text
Authenticated client + XSRF proof
        |
        v
ThreatIntelligenceController
        |-- validates bounded type/category/value input
        |-- canonicalizes and redacts an indicator
        |-- derives a keyed HMAC-SHA-256 matching hash
        |-- rejects a duplicate report from the same user
        v
ThreatIntelligenceService
        |-- reports -> canonical threats (UNVERIFIED by default)
        |-- audit_logs (THREAT.REPORTED / THREAT.VERIFICATION_UPDATED)
        |-- safe search, statistics, and campaign candidates
        v
Privacy-redacted authenticated response
```

## Privacy model

The original value is in memory only long enough to validate, canonicalize, and derive a
deterministic matching value. The database receives a safe display label plus a keyed
HMAC-SHA-256 digest. This is materially safer than a plain SHA-256 digest for low-entropy input
such as a phone number, which is otherwise easy to enumerate offline.

Set `TRUSTLAYER_INDICATOR_HASH_SECRET` to an independent high-entropy value in production. The
local-development fallback is `TRUSTLAYER_JWT_SECRET`, so existing developer environments still
start; that fallback is not acceptable production key separation.

| Submitted type | Safe response/database display | Hash input |
| --- | --- | --- |
| `URL` | Parsed scheme/host/port with path/query redacted | Normalized redacted URL display |
| `DOMAIN` | Normalized ASCII domain | Normalized domain |
| `PHONE` | `Phone number ending 1234` | Normalized full phone number, never exposed |
| `QR_PAYLOAD` | `QR payload (redacted)` | Normalized payload, never exposed |
| `BENEFICIARY` | `Beneficiary identifier (redacted)` | Normalized identifier, never exposed |
| `OTHER` | `Community indicator (redacted)` | Normalized input, never exposed |

Descriptions are restricted moderation context and never appear in a public API response. The
submitted indicator is redacted before persistence; URL-looking and phone-looking content is also
redacted for those report types. This is not a personal-data detection service. Do not enter bank
details, account numbers, passwords, or other personal information into a description.

## Report lifecycle

1. The first report creates a canonical `Threat` with status `UNVERIFIED` and count one.
2. An independent reporter for the same canonical value increments the count.
3. The original reporter cannot inflate the count: the service returns `409 DUPLICATE_REPORT`,
   and a database uniqueness constraint is the final enforcement layer.
4. At three reports, a threat becomes a **repeated-report candidate**. This is a frequency
   heuristic, not a confirmed campaign or proof of fraud.
5. Only an already provisioned database-backed `ADMIN` can change the canonical verification
   status. Self-registration always creates `USER`; no public API can promote a user.

Administrative verification is a human accountability action, never an automated classifier.
Production still needs a controlled initial-admin provisioning process and an evidence-review
policy before it should rely on this route.

## REST API

All routes require a valid bearer access token. `POST` and `PATCH` also require the current
`XSRF-TOKEN` cookie and matching `X-XSRF-TOKEN` header.

### POST `/api/threats/report`

```http
Authorization: Bearer <access-token>
X-XSRF-TOKEN: <csrf-token>
Cookie: XSRF-TOKEN=<csrf-token>
Content-Type: application/json
```

```json
{
  "indicatorType": "URL",
  "indicatorValue": "https://paypa1-security.example.zip/login?token=do-not-store-this",
  "category": "PHISHING",
  "description": "A recipient was asked to sign in urgently."
}
```

It returns `201 Created`:

```json
{
  "success": true,
  "data": {
    "reportId": 42,
    "reportStatus": "PENDING",
    "createdNewThreat": true,
    "corroboratesExistingThreat": false,
    "threat": {
      "threatId": 18,
      "indicatorType": "URL",
      "indicator": "https://paypa1-security.example.zip/[path-redacted]?[query-redacted]",
      "category": "PHISHING",
      "verificationStatus": "UNVERIFIED",
      "communityReportCount": 1,
      "repeatedCampaignCandidate": false,
      "verificationNotice": "Community reports are unverified submissions and do not prove fraud or maliciousness."
    }
  }
}
```

The raw query value, full phone number, QR payload, beneficiary, and description are not returned.

### GET `/api/threats`

Returns a stable page envelope. `page` defaults to zero, `size` defaults to 20, and size is capped
at 100.

```text
GET /api/threats?type=PHONE&query=%2B91%2098765%2043210
GET /api/threats?category=PHISHING&verificationStatus=UNVERIFIED&page=0&size=20
```

When both `type` and `query` are supplied, the backend creates an HMAC for the query and finds an
exact canonical record without returning the raw stored value.

### GET `/api/threats/{threatId}`

Returns one safe canonical record. A missing record returns `404 THREAT_NOT_FOUND`.

### GET `/api/threats/statistics`

Returns real database counts by verification status and category plus repeated-report candidates.
It does not claim every row is malicious.

### GET `/api/threats/campaigns?minReports=3`

Returns at most twenty records meeting the supplied independent-report threshold. `minReports`
ranges from 2 through 1000. The response message explicitly labels these as candidates.

### PATCH `/api/admin/threats/{threatId}/verification`

Requires an authenticated account whose **current database role** is `ADMIN`.

```json
{
  "verificationStatus": "VERIFIED"
}
```

`VERIFIED` sets confidence to 100, `REJECTED` sets it to 0, and `UNVERIFIED` clears it. The action
is audited. These values describe human review state, not model confidence.

## Errors and safeguards

| Status | Code | Cause |
| ---: | --- | --- |
| 400 | `INVALID_THREAT_INDICATOR` | Indicator does not meet the selected type's safety/syntax boundary. |
| 400 | `INVALID_REPORT_DESCRIPTION` | Description has unsupported controls or exceeds its limit. |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, invalid, expired, revoked, or inactive session token. |
| 403 | `ACCESS_DENIED` | Missing/invalid CSRF proof or ordinary user attempted an admin action. |
| 404 | `THREAT_NOT_FOUND` | Canonical record does not exist. |
| 409 | `DUPLICATE_REPORT` | Same account already reported that canonical indicator. |
| 429 | `THREAT_REPORT_RATE_LIMITED` | Per-user report allowance exceeded. |

`TRUSTLAYER_THREAT_MAX_REPORTS_PER_MINUTE` defaults to 10 per user per minute per application
instance. This is an in-memory single-instance safeguard. Deploy a shared limiter before scaling
horizontally.

## Test and Postman verification

Import `postman/TRUSTLAYER-Phase-8-Threat-Intelligence.postman_collection.json`, complete Phase 3
sign-in, set `accessToken`, and leave Postman's cookie jar enabled.

The integration tests prove CSRF rejection, first report, independent corroboration,
same-reporter duplicate rejection, redacted phone search, absence of raw URL secrets from both API
output and persisted description, frequency candidates remaining unverified, and normal-user
denial at the admin route before success for a test-provisioned database admin.

```zsh
cd /Users/prathamjindal/Documents/Codex/2026-11-16/trustlayer-ultimate-master-development-prompt-ai/backend
./mvnw -ntp test
TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw -ntp verify
```

## Known limitations

- There is no external threat feed, moderator queue, evidence attachment workflow, or campaign
  graph.
- HMAC matching mitigates simple offline hash enumeration but cannot protect a compromised
  application/database or a malicious trusted moderator.
- Coordinated account abuse can inflate report frequency. The rate limiter and duplicate rule
  reduce that risk but do not eliminate it.
- The initial-admin provisioning workflow is deliberately not public and is deferred to the
  production-hardening phase.
