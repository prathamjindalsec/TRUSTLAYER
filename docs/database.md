# TRUSTLAYER Phases 2–11 — MySQL database design

## Scope and implementation model

The authoritative schema is the ordered Flyway migration set:

    backend/src/main/resources/db/migration/V1__create_trustlayer_schema.sql
    backend/src/main/resources/db/migration/V2__add_authentication_tokens.sql
    backend/src/main/resources/db/migration/V3__add_transaction_confidence_level.sql
    backend/src/main/resources/db/migration/V4__add_extension_authorization_codes.sql

Do not create a separate hand-maintained schema.sql. Two independently edited schemas drift and make deployments unsafe. A future database change must be a new immutable Flyway migration, for example V3__add_feature.sql.

V1 is the Phase 2 baseline and is already immutable. V2 is the Phase 3 additive authentication migration: it adds users.password_changed_at plus the verification and reset token tables. V3 adds transactions.confidence_level for the Phase 7 simulated transaction-risk decision. V4 adds one-time, PKCE-bound extension authorization-code storage. Do not edit an applied file; create V5 or later for all future changes.

The local development service is MySQL 8.4 LTS bound to 127.0.0.1:3306. Production must use MySQL 8+ with TLS, backups, restricted network access, secret management, and a separately controlled migration job.

## Tables and ownership

| Table | Purpose | Key relationships |
| --- | --- | --- |
| users | Account identity and lifecycle state. Passwords are hashes only. | Parent of preferences and sessions; referenced by activity records. |
| user_preferences | One row per user for theme, locale, privacy, notification and cookie preferences. | users to user_preferences cascade on a hard purge. |
| authentication_sessions | Revocable session metadata and hashes of session tokens. | users to authentication_sessions cascade on a hard purge. |
| email_verification_tokens | One-time email-verification token hashes and expiry metadata. Raw tokens are never persisted. | users to email_verification_tokens cascade on a hard purge. |
| password_reset_tokens | One-time password-reset token hashes and expiry metadata. Raw tokens are never persisted. | users to password_reset_tokens cascade on a hard purge. |
| extension_authorization_codes | One-time SHA-256 authorization-code hashes for a consented Chrome-extension URL-scan grant. | users to extension_authorization_codes cascade on a hard purge; each row references a still-active authentication session by ID. |
| threats | Canonical threat indicators with verification state. | May be verified by a user and referenced by reports. |
| scans | URL or QR scan requests and processing lifecycle. | Optional requesting user; one scan result. |
| scan_results | Explainable risk decision for one scan. | One-to-one with scans; parent of scan risk indicators. |
| transactions | Simulated transaction analysis data only. | Optional user; parent of transaction risk indicators. |
| risk_indicators | Weighted explainable evidence for a scan result or transaction, never both. | Foreign keys and a database check constraint enforce the exact owner. |
| reports | Community-submitted indicators and moderation state. | Optional reporter and canonical threat. |
| audit_logs | Security-relevant audit trail. | Optional actor, deliberately retained if an account is hard-purged. |

## Risk-score integrity

Risk scores use TINYINT UNSIGNED with database CHECK constraints enforcing the inclusive range 0–100. The risk_indicators table records:

- the exact indicator code and human-readable name;
- an evidence state: CONFIRMED_MALICIOUS, SUSPICIOUS, or UNKNOWN;
- the indicator weight and severity;
- structured evidence as JSON.

This prevents the future analyzer from returning unexplained arbitrary scores.

## Indexes

Indexes cover the expected hot paths:

- email lookup and user lifecycle filtering;
- active-session lookup by user, session identifier, revocation state, and expiration;
- active verification-token and reset-token lookup by user and expiry;
- scan history by user, type, and date plus target de-duplication;
- risk classification and reporting queries;
- simulated transaction history and beneficiary-pattern analysis;
- threat search and report frequency;
- audit lookup by actor, entity, event, and date.

Do not add indexes based on guesswork beyond these baseline access paths. Measure real query plans before adding more.

## Deletion and retention policy

Account deletion is a soft delete using users.deleted_at and users.status = DELETED. A future privacy workflow can hard-purge data only after legal and product retention requirements are defined.

| Data | Suggested policy |
| --- | --- |
| Revoked or expired session metadata | Delete 30 days after expiry or revocation. |
| Consumed or expired verification and reset token hashes | Delete within 30 days after expiry or consumption. |
| Consumed or expired extension authorization-code hashes | Delete promptly; their useful life is at most one minute. |
| Privacy-redacted scanner target displays and reports | Retain only as long as necessary for fraud prevention and dispute handling. Raw submitted URL credentials, path values, query values, and fragments are not persisted by Phase 4. |
| Audit events | Retain for 12 months unless a documented legal or security requirement differs. |
| Threat records | Retain while useful; mark rejected records rather than deleting community history. |
| Simulated transaction data | Delete with demonstration/test-data reset procedures; never treat it as banking data. |

## Database roles

The development setup creates two accounts restricted to 127.0.0.1:

- trustlayer_app has SELECT, INSERT, UPDATE, and DELETE privileges only on the trustlayer database.
- trustlayer_migrator has schema-migration privileges on the trustlayer database and is used by Flyway.

Application database credentials are stored in the ignored file backend/.env. The tracked backend/.env.example contains placeholders only. Nothing in source control contains a usable password.

## Phase 3 authentication data handling

- users.password_hash holds a BCrypt hash only. Plaintext passwords are never written to the database or audit log.
- authentication_sessions.session_token_hash stores the SHA-256 hash of a high-entropy refresh JWT, never the raw refresh JWT.
- email_verification_tokens.token_hash and password_reset_tokens.token_hash store SHA-256 hashes of high-entropy, single-use action tokens. The raw value exists only long enough to be delivered to the user or exposed in explicitly enabled local development.
- The session, verification, and reset hash columns are fixed-width ASCII CHAR columns. The JPA mappings specify the same type so Hibernate validation detects schema drift instead of silently accepting it.
- users.deleted_at and users.status=DELETED implement Phase 3 soft deletion. Sessions are revoked when an account is soft-deleted.
- audit_logs retains security events such as successful sign-in, failed sign-in for an existing account, password-reset lifecycle, logout, session revocation, profile changes, and account deletion. Its foreign key uses ON DELETE SET NULL so audit records can survive a future hard purge.

## Phase 4 and Phase 6 scanner data handling

Phases 4 and 6 use the scan-related tables already provisioned in V1. They do not alter an applied migration merely because Java mappings were added later.

- `scans` receives one `URL` or `QR` row per accepted scan. `user_id` scopes history and detail access; `target_value` is a privacy-redacted display target, never raw URL secrets or raw QR payload data; `target_hash` is SHA-256 of that safe display value; and `status` ends as `COMPLETED` for the synchronous workflow.
- `scan_results` stores exactly one result per scan through its unique `scan_id`. `risk_score` remains database-constrained to 0–100. `explanation` is structured JSON containing the formula, individual rule codes and weights, safe counts, recommendations, limitations, image metadata, and analyzer version.
- `risk_indicators` stores one row for every triggered static rule. URL and QR phases set `scan_result_id` and leave `transaction_id` null; the database check constraint enforces that exact-one-owner model. Evidence JSON contains only safe structural facts such as a TLD, fixed rule name, boolean field presence, an image dimension, or a count; it must not contain raw URL credentials, paths, query values, fragments, QR text, payees, amounts, notes, or transaction references.
- `audit_logs` records `SCANNER.URL_ANALYZED` or `SCANNER.QR_ANALYZED` with the scan ID. It does not record raw submitted data.

The existing `idx_scans_user_requested`, `idx_scan_results_risk`, `idx_scan_results_analyzed`, and `idx_indicators_scan_result` indexes support the Phase 4 owner-scoped history and detail paths. The history repository uses user ID, scan type, and request time; it does not search raw URL input.

The static scanner's redaction is a data-minimization choice. It means future duplicate/campaign matching cannot rely on exact secret-bearing paths or query values.

## Phase 8 community-intelligence data handling

The `threats` and `reports` tables were deliberately included in V1 and are now used without
rewriting that immutable migration.

- `threats` stores a public-safe indicator display value, a 64-character HMAC-SHA-256 matching
  digest, category, verification state, report count, frequency timestamps, and any authorized
  verifier. It never stores a raw phone number, raw QR payload, or raw beneficiary identifier.
- `reports` preserves one user submission per reporter/type/keyed-hash combination. Its uniqueness
  constraint is the final backstop against self-corroboration; the service checks it first to
  return `409 DUPLICATE_REPORT` rather than a database error.
- `reports.description` is restricted moderator context, not public API data. The submitted
  indicator is redacted before persistence. It remains subject to retention and future personal
  data deletion policy; it is not a place to collect payment credentials or personal information.
- Every community-created canonical row remains `UNVERIFIED`, regardless of report count.
  `report_count >= 3` means only that it is a repeated-report candidate.
- `uq_threats_indicator`, `idx_threats_status_category`, `idx_threats_report_count`,
  `idx_threats_last_reported`, and `idx_reports_threat_status` support exact matching, safe
  filtering, frequency ordering, and report workflow lookups.

`TRUSTLAYER_INDICATOR_HASH_SECRET` supplies the HMAC key. Production must use a high-entropy
secret independent from the JWT key. The code permits the JWT secret as a development fallback so
an existing local `.env` keeps working, but that fallback is not production key management.

## Phase 9 dashboard aggregation

Phase 9 does not introduce a dashboard table or a cache of decorative totals. `DashboardService`
aggregates existing records at request time:

- owner-scoped URL/QR scan counts come from `scans` and `scan_results`;
- owner-scoped simulation totals and risk distribution come from `transactions`;
- current platform-wide community totals come from `threats` and accepted `reports`;
- recent activity is assembled from the same owner-scoped scans, simulations, and reports.

The pre-existing history, risk, and report-frequency indexes support these bounded aggregations.
The dashboard deliberately avoids raw values: it returns the previously redacted display values
from the application tables. If production traffic makes the aggregation expensive, measure real
MySQL query plans first; then introduce a well-documented bounded cache or read model rather than
hardcoding stale counters.

## Phase 11 extension authorization data

`V4__add_extension_authorization_codes.sql` adds an additive, short-lived authorization-code table.
It is not a second session table and it does not store a browser access token, a refresh cookie, or
the raw code delivered to Chrome.

- `code_hash` is an ASCII SHA-256 digest of a high-entropy one-time code. The raw code exists only
  in the HTTPS Chrome identity callback long enough for the extension to exchange it with its PKCE
  verifier.
- `client_id`, `redirect_uri`, `code_challenge`, `scope`, and `session_id` bind exchange to the
  exact installed extension, its callback, the S256 verifier, its sole `url:scan` capability, and
  the originating server-side session.
- `expires_at` is indexed for cleanup; `consumed_at` records one-time use. A unique hash prevents
  accidental code collision. The user/creation index supports a future bounded cleanup or account
  audit without indexing raw browser data.
- V4 has been validated against the local MySQL 8.4 schema. It uses `ON DELETE CASCADE` from the
  account so a future hard account purge cannot leave an orphaned grant record behind.

## Local-development TLS limitation

The ignored development .env uses a loopback-only JDBC URL with useSSL=false and allowPublicKeyRetrieval=true so the local MySQL account can authenticate without a custom certificate trust store. This is acceptable only for localhost development. Production must use TLS with certificate validation and must not use that JDBC URL.
