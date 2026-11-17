# TRUSTLAYER Phase 4 — static phishing URL detection

## Purpose and honest boundary

Phase 4 provides a working, authenticated URL scanner with database-backed history and an explainable, deterministic risk score. It is deliberately **not** a claim that TRUSTLAYER can prove a website is safe or malicious.

The implementation accepts only absolute `http` and `https` URLs. It statically parses the submitted text, applies local structural rules, persists the result, and returns the reasons, weights, recommendations, confidence, and limitations. It does **not** request the destination.

That boundary is a security decision, not a missing convenience feature. Letting an API client make the backend fetch arbitrary URLs would create an SSRF and internal-network exposure risk.

Not implemented in this phase:

- DNS, reverse DNS, domain-age, WHOIS, TLS-certificate, HTTP-content, redirect-chain, or HTTP-header inspection;
- an external reputation/threat-intelligence provider;
- a verified-malicious classification based on community reports;
- a trained phishing model, calibration data, false-positive/false-negative evaluation dataset, or any claim of AI prediction;
- QR scanning (Phase 6) and transaction analysis (Phase 7).

## Architecture

```text
Authenticated client
       |
       | Bearer access JWT + XSRF-TOKEN cookie/header for POST
       v
ScannerController
       |
       v
UrlScannerService ----> per-user, in-process submission limiter (30/min default)
       |
       +--> UrlSafetyAnalyzer
       |      - java.net.URI / IDN parsing only
       |      - no DNS lookup
       |      - no socket or HTTP client
       |      - static weighted indicators
       |
       +--> scans -> scan_results -> risk_indicators
       +--> audit_logs (SCANNER.URL_ANALYZED)
       v
Explainable JSON response and owner-scoped history
```

The controller has no database logic. The service enforces ownership and persistence. The analyzer is pure, deterministic, and testable without a network or database. `TokenHashService` hashes the redacted canonical display target before persistence; the raw submitted URL is never retained.

## Relevant source layout

```text
backend/src/main/java/com/trustlayer/
├── analyzer/
│   ├── UrlAnalysis.java
│   └── UrlSafetyAnalyzer.java
├── config/
│   └── ScannerProperties.java
├── controller/
│   └── ScannerController.java
├── dto/scanner/
│   ├── UrlScanRequest.java
│   ├── UrlScanResponse.java
│   ├── UrlScanHistoryItemResponse.java
│   └── UrlRiskIndicatorResponse.java
├── entity/
│   ├── Scan.java
│   ├── ScanResult.java
│   └── RiskIndicator.java
├── repository/
│   ├── ScanRepository.java
│   ├── ScanResultRepository.java
│   └── RiskIndicatorRepository.java
└── service/
    ├── UrlScannerService.java
    └── UrlScanSubmissionRateLimiter.java
```

## Stored data and privacy

The `scans`, `scan_results`, and `risk_indicators` tables were created in the immutable Phase 2 baseline migration. Phase 4 maps and uses them; it does not rewrite a migration that has already been applied.

The scanner never stores or returns the exact raw URL. In particular, it redacts:

- user-info / URL credentials, such as `user:password@`;
- every path value;
- every query value;
- every fragment.

For example, this submitted URL:

```text
http://user:secret@192.0.2.1/reset?token=private-token&redirect=https://example.test
```

is stored and returned only as:

```text
http://192.0.2.1/[path-redacted]?[query-redacted]
```

The analysis retains safe metadata such as a query-parameter count, the fixed name `redirect` when it matches an internal rule, and an indicator category. It never saves query values or credentials as evidence.

## Risk engine v1

The calculation is completely deterministic:

```text
risk score = min(100, sum of every detected static rule weight)
```

The result JSON exposes the raw weighted score, final capped score, rule codes, per-rule weights, each indicator's evidence, classification, confidence, recommendations, and limitations. All Phase 4 indicators use `SUSPICIOUS`; none use `CONFIRMED_MALICIOUS` because this phase uses no verified intelligence source.

| Rule code | Static condition | Weight | Meaning and limitation |
| --- | --- | ---: | --- |
| `URL_NOT_HTTPS` | Scheme is HTTP | 12 | Transport is not encrypted; HTTPS earns no safety credit. |
| `URL_USERINFO_OBFUSCATION` | URL contains `userinfo@host` | 20 | Can visually disguise the real host. |
| `IP_ADDRESS_HOST` | Host is a direct IPv4/IPv6 literal | 30 | Legitimate services can use IPs; this is a strong structural signal, not proof. |
| `PRIVATE_OR_LOCAL_HOST` | Loopback, RFC1918/link-local/private IPv6, `.local`, or localhost | 18 | No network lookup occurs. |
| `IDN_OR_PUNYCODE_HOST` | Unicode/`xn--` host | 20 | Internationalized domains can be legitimate, but need careful verification. |
| `URL_SHORTENER` | Exact known shortener host | 15 | Destination is hidden; no redirect is followed. |
| `HIGH_ABUSE_TLD` | TLD is in the local abuse-oriented rule list | 10 | TLD alone never proves abuse. |
| `BRAND_LOOKALIKE` | Non-official host structurally resembles a protected brand | 30 | Local edit-distance/string heuristic, not brand ownership verification. |
| `EXCESSIVE_SUBDOMAINS` | Three or more pre-domain labels | 8 | Deep labels can obscure the registrable domain. |
| `NON_STANDARD_PORT` | Explicit port differs from 80/443 | 8 | Some legitimate services use alternate ports. |
| `LONG_URL` | More than 180 / 400 input characters | 6 / 12 | Long URLs can conceal intent. |
| `MANY_QUERY_PARAMETERS` | Six or more query parameters | 6 | Query complexity alone is weak evidence. |
| `REDIRECT_PARAMETER` | Query key matches redirect-like names | 8 | Only the parameter name is retained. |
| `SUSPICIOUS_KEYWORD_PATTERN` | Credential, financial, urgency, or crypto category | 4 each, cap 12 | Text terms are weak contextual signals. |
| `EXCESSIVE_PERCENT_ENCODING` | Three or more `%xx` escapes | 6 | Encoding may be legitimate. |
| `OBFUSCATED_DOMAIN_LABEL` | Mixed digit/letter or multiple-hyphen host label | 6 | Heuristic only. |

| Score | Classification |
| ---: | --- |
| 0–19 | `LOW` |
| 20–49 | `MEDIUM` |
| 50–79 | `HIGH` |
| 80–100 | `CRITICAL` |

`LOW` means none of this version's configured static rules fired. It never means “safe.” Confidence is deliberately capped at `MEDIUM` because no external source confirms the result.

## Configuration and startup

The project remains locked to Java 17. Start MySQL and ensure the ignored `backend/.env` is already configured as in Phases 2–3.

| Variable | Default | Meaning |
| --- | ---: | --- |
| `TRUSTLAYER_SCANNER_MAX_SUBMISSIONS_PER_MINUTE` | 30 | Maximum URL scans by one authenticated user in a rolling minute, per application instance. |

The limit is an in-memory single-instance safeguard. It is not suitable for horizontal scale; replace it with a shared gateway or Redis-backed control before production deployment.

```zsh
cd /Users/prathamjindal/Documents/Codex/2026-11-16/trustlayer-ultimate-master-development-prompt-ai/backend
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

If port 8080 is occupied:

```zsh
SERVER_PORT=8081 ./mvnw spring-boot:run
```

On startup, Flyway validates the database and Hibernate validates the existing mappings. Neither tool creates scanner tables dynamically in the production profile.

## REST API

All scanner routes require a valid access JWT. Since the browser authentication architecture includes a refresh cookie, `POST /api/scanner/url` also requires the matching CSRF cookie/header pair. Read-only `GET` routes do not.

### POST `/api/scanner/url`

Request:

```http
Authorization: Bearer <access-token>
X-XSRF-TOKEN: <csrf-token>
Cookie: XSRF-TOKEN=<csrf-token>
Content-Type: application/json
```

```json
{
  "url": "https://paypa1-secure.example.zip/login"
}
```

Success: HTTP `201 Created`.

```json
{
  "success": true,
  "message": "URL analysis completed without connecting to the destination.",
  "data": {
    "scanId": 41,
    "targetUrl": "https://paypa1-secure.example.zip/[path-redacted]",
    "status": "COMPLETED",
    "riskScore": 54,
    "riskClassification": "HIGH",
    "confidenceLevel": "MEDIUM",
    "analyzerVersion": "url-static-rules-v1",
    "indicators": [
      {
        "code": "BRAND_LOOKALIKE",
        "name": "Potential brand impersonation in the host",
        "type": "SUSPICIOUS",
        "severity": "HIGH",
        "weight": 30,
        "evidence": { "matchedBrands": ["paypal"] }
      }
    ],
    "recommendations": [
      "Do not enter credentials, one-time passwords, payment details, or approve a transaction from this destination."
    ],
    "limitations": [
      "Static local URL analysis only; TRUSTLAYER did not connect to this destination."
    ],
    "explanation": {
      "scoreFormula": "min(100, sum of detected static rule weights)",
      "confirmedMaliciousIndicatorCount": 0
    }
  },
  "timestamp": "runtime-generated ISO-8601 timestamp"
}
```

| Status | Code | Cause |
| ---: | --- | --- |
| 400 | `VALIDATION_FAILED` | Missing, blank, or over-2048-character `url`. |
| 400 | `INVALID_URL` | Not an absolute HTTP(S) URL or invalid authority syntax. |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, expired, revoked, or invalid access token. |
| 403 | `ACCESS_DENIED` | Missing/incorrect CSRF proof. |
| 429 | `SCAN_RATE_LIMITED` | More than the configured submissions within one rolling minute. |

### GET `/api/scanner/history?page=0&size=20`

Returns only the authenticated user's URL scans, newest first.

Success: HTTP `200 OK`.

```json
{
  "success": true,
  "message": "URL scan history retrieved.",
  "data": {
    "items": [
      {
        "scanId": 41,
        "targetUrl": "https://paypa1-secure.example.zip/[path-redacted]",
        "status": "COMPLETED",
        "riskScore": 54,
        "riskClassification": "HIGH",
        "confidenceLevel": "MEDIUM"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

Invalid page/size values return HTTP `400 VALIDATION_FAILED`; no access token returns HTTP `401`.

### GET `/api/scanner/{scanId}`

Returns the full explainable result only when the scan belongs to the authenticated user. A scan owned by another user deliberately returns the same response as a non-existent ID:

```json
{
  "success": false,
  "code": "SCAN_NOT_FOUND",
  "message": "Scan was not found.",
  "fieldErrors": {}
}
```

That is HTTP `404`; it prevents a user from discovering another user's scan IDs.

## Postman workflow

1. Run the Phase 3 authentication collection first with `TRUSTLAYER_EXPOSE_ACTION_TOKENS=true` only in local development. Verify the user and sign in.
2. Copy the access JWT into `accessToken` in `postman/TRUSTLAYER-Phase-4-URL-Scanner.postman_collection.json`.
3. Import the Phase 4 collection.
4. Run **Get CSRF token**. Postman must retain cookies for `localhost`; the test script stores the token in `csrfToken`.
5. Run **Scan suspicious URL**, then **Scan history**, then **Get scan by ID**.

The scanner sample URL is a text string only. TRUSTLAYER does not browse it during analysis.

## Exact automated checks

Run the isolated suite:

```zsh
cd /Users/prathamjindal/Documents/Codex/2026-11-16/trustlayer-ultimate-master-development-prompt-ai/backend
./mvnw -ntp -Dmaven.repo.local=../work/m2 test
```

Run the complete suite, including opt-in local MySQL Flyway/Hibernate validation:

```zsh
TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw -ntp -Dmaven.repo.local=../work/m2 verify
```

The Phase 4 checks cover clean HTTPS input, direct-IP HTTP/redirect signals, lookalike/TLD signals, official-brand false-positive protection, malformed scheme rejection, CSRF enforcement, persistence, redaction, owner-scoped history/details, IDOR resistance, rolling scan throttling, and actual MySQL mapping validation.

## Verification result — 2026-11-16

The following commands were actually executed after the Phase 4 implementation:

| Command | Result |
| --- | --- |
| `./mvnw -ntp -Dmaven.repo.local=../work/m2 test` | 20 tests run; 0 failures; 0 errors; 1 skipped because the MySQL-only test is intentionally opt-in. |
| `TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw -ntp -Dmaven.repo.local=../work/m2 verify` | 20 tests run; 0 failures; 0 errors; 0 skipped. Flyway validated schema versions 1 and 2 and Hibernate validated the mappings against local MySQL 8.4.13. |
| Live localhost workflow on port 8081 | Passed registration, verification, login, CSRF issuance, `POST /api/scanner/url`, `GET /api/scanner/history`, and `GET /api/scanner/{id}` against MySQL. The scan scored 54 / `HIGH` and returned `HIGH_ABUSE_TLD`, `BRAND_LOOKALIKE`, `SUSPICIOUS_KEYWORD_PATTERN`, and `OBFUSCATED_DOMAIN_LABEL`. The temporary app was gracefully stopped afterward. |

The live workflow created one disposable development account and one scan record in the local `trustlayer` database. It did not contact the submitted external URL.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| 401 on history/detail | The bearer JWT is missing, expired, or session-revoked. | Sign in again and send `Authorization: Bearer <access-token>`. |
| 403 on POST scan | CSRF header/cookie pair is absent or mismatched. | GET `/api/auth/csrf`, keep `XSRF-TOKEN`, and send its exact value in `X-XSRF-TOKEN`. |
| 400 `INVALID_URL` | A non-HTTP(S), relative, malformed, or invalid-port URL was sent. | Send a complete `http://` or `https://` URL. |
| 429 `SCAN_RATE_LIMITED` | User crossed the per-minute limit. | Wait for the rolling minute; do not disable the safeguard. |
| `SCAN_NOT_FOUND` for a known ID | The ID is not owned by the current user or the URL scan does not exist. | Use the account that created the scan. |
| MySQL validation fails | Local Flyway schema or credentials are not in the Phase 2–3 state. | Fix `.env` and migration history; never edit an applied migration. |

## Next phase

Phase 5 extracted the scoring primitives into a centralized risk engine now shared by URL, QR, and transaction modules. It does not silently relabel static indicators as ML results. Any future external inspection needs explicit egress controls, DNS-rebinding defenses, allowlisted provider APIs, timeouts, and auditable evidence provenance.
