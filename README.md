
# TRUSTLAYER — Hackathon MVP through Phase 16

TRUSTLAYER is a financial fraud-detection and cybersecurity platform. This repository contains a
functional Java/Spring Boot and React hackathon MVP plus production-hardening source material from
Phases 1–16. The current decision engine is transparent and rule-based; it is not a trained
machine-learning classifier, a live reputation service, or a payment-control system.

Implemented source and verified local behavior include:

- a Java 17 / Spring Boot 3.5.16 backend;
- a MySQL 8.4 schema managed only through Flyway;
- secure account registration, email-verification workflow, sign-in, signed JWT access tokens, rotating refresh tokens, logout, password reset, profiles, login history, and active-session management;
- an authenticated URL phishing-risk scanner with transparent weighted rules, privacy-redacted scan storage, owner-scoped history, individual indicator evidence, and no outbound requests to submitted destinations;
- a centralized weighted-risk engine that validates evidence provenance and is shared by current and future analyzer modules;
- an authenticated PNG/JPEG QR scanner with local-only decoding, static URL integration, safe UPI/payment/action/text classification, image-upload limits, privacy-redacted persistence, and no payment or navigation execution;
- an authenticated, explainable transaction-risk simulator that compares user-entered demo scenarios against same-user simulated history without storing raw beneficiary identifiers or touching real payment rails;
- authenticated community threat reporting, privacy-redacted canonical matching, duplicate-report prevention, database-backed statistics, repeated-report candidates, and separately protected human-admin verification;
- a responsive React/Tailwind application with real authentication, URL/QR scanning, transaction-simulation, community-intelligence, account-management, privacy-policy, cookie-choice, dark-mode, and dashboard flows;
- an authenticated dashboard aggregation endpoint whose values are computed from persistent records rather than frontend demonstration data;
- a verified same-origin browser/API integration model: Vite proxies local `/api` requests without broad credentialed CORS, protected mutations obtain a current CSRF pair, and StrictMode-safe session restoration prevents refresh-token rotation races;
- a Manifest V3 Chrome extension with opt-in static URL checks, privacy-preserving target sanitization, user-controlled automatic checks, and a short-lived PKCE-bound URL-scan-only token instead of copied web credentials;
- keyboard and small-screen UX hardening: skip links, focus-managed layers/dialogs, named live regions, improved field associations, touch-sized controls, and scanner-table captions;
- automated H2 integration tests plus an opt-in real-MySQL schema-validation test;
- security-regression coverage for defensive headers, malformed requests, unauthenticated access, and production-safe configuration rejection;
- Dockerfiles, a loopback-only Compose demo topology, Nginx proxy/security headers, a production safety profile, deployment guidance, architecture diagrams, a final testing report, and a judge-safe demo runbook.

The source now covers the planned phases, but the project does **not** meet the original prompt's
full production-ready standard. Docker is unavailable on this Mac so images and Compose were not
run; the Java CVE feed scan did not complete; the extension was not manually connected in Chrome;
and public TLS, email delivery, backup recovery, load testing, a device/browser matrix, and an
independent security review remain incomplete. See [docs/final-testing-report.md](docs/final-testing-report.md)
before making any security or production claim.

## What Phase 3 actually delivers

- BCrypt password hashes with a cost factor of 12; plaintext passwords are never persisted.
- A password policy requiring at least 12 characters, upper- and lower-case letters, a digit, a symbol, and no more than BCrypt's 72 UTF-8-byte effective input limit.
- Email verification and password-reset records that store only SHA-256 hashes of cryptographically random, one-time tokens.
- HMAC-signed JWT access tokens that expire after 15 minutes by default.
- HttpOnly, SameSite=Strict refresh-token cookies that expire after seven days by default.
- Refresh-token rotation and reuse detection. A replayed stale refresh token revokes its session.
- Database-backed sessions. Each protected bearer-token request checks that the originating session remains active, so logout, password reset, per-session revocation, and account deletion invalidate an access token immediately.
- CSRF protection for every state-changing operation that relies on the browser cookie.
- Role model with USER, ANALYST, and ADMIN roles. USER is the only self-registration role; administrative routes fail closed unless an ADMIN role is supplied from the database.
- Profile retrieval and update, active-session listing and revocation, paginated login history, logout from all devices, and soft account deletion.
- Structured audit events for security-relevant account activity.
- A conservative per-process login throttle: five failures for the same email and source address within 15 minutes cause a 15-minute block.

The complete Phase 3 endpoint contract, example requests, Postman order, error contract, and security limitations are in [docs/authentication.md](docs/authentication.md).

The Phase 4 URL scanner contract is in [docs/url-scanner.md](docs/url-scanner.md), the shared score contract is in [docs/risk-scoring.md](docs/risk-scoring.md), the Phase 6 QR scanner contract is in [docs/qr-scanner.md](docs/qr-scanner.md), the Phase 7 simulation contract is in [docs/transaction-risk.md](docs/transaction-risk.md), the Phase 8 intelligence contract is in [docs/threat-intelligence.md](docs/threat-intelligence.md), the Phase 9 browser/dashboard architecture and run instructions are in [docs/frontend-dashboard.md](docs/frontend-dashboard.md), the Phase 10 browser/API contract is in [docs/frontend-integration.md](docs/frontend-integration.md), the Phase 11 extension security boundary is in [docs/browser-extension.md](docs/browser-extension.md), and the Phase 12 UX/accessibility work is in [docs/ui-accessibility.md](docs/ui-accessibility.md).

Phase 13 security results are in [docs/security-assessment.md](docs/security-assessment.md), Phase 14 quality results are in [docs/quality-testing.md](docs/quality-testing.md), the system design is in [docs/architecture.md](docs/architecture.md), local Docker and production deployment boundaries are in [docs/deployment.md](docs/deployment.md), and the live judging script is in [docs/final-demo.md](docs/final-demo.md).

## What Phase 4 actually delivers

- `POST /api/scanner/url`, `GET /api/scanner/history`, and `GET /api/scanner/{id}`.
- Static parsing of absolute HTTP(S) URLs only. The backend does **not** resolve DNS, open a socket, fetch content, follow redirects, inspect a certificate, look up WHOIS, or query a reputation service.
- Transparent weights for direct IPs, HTTP, URL user-info, punycode, shorteners, high-abuse TLDs, lookalike brands, deep subdomains, ports, query/redirect patterns, keywords, percent encoding, and obfuscated labels.
- MySQL persistence through the existing `scans`, `scan_results`, and `risk_indicators` tables, plus security audit events.
- Privacy-conscious target storage: URL credentials, path values, query values, and fragments are redacted before persistence and response serialization.
- Per-user rolling scan-submission throttle, defaulting to 30 requests/minute per application instance.
- CSRF protection for the scanner POST, JWT authorization, and 404-on-other-user IDs to prevent IDOR disclosure.

Every Phase 4 result explicitly says that it is a static local assessment. `LOW` does not mean safe, and no Phase 4 indicator is classified as `CONFIRMED_MALICIOUS`.

## Important honesty about email

There is no fake mail provider hidden behind this code.

For local development only, setting TRUSTLAYER_EXPOSE_ACTION_TOKENS=true exposes a temporary verification or reset token in the API response so the workflow can be tested. The local ignored backend/.env is configured this way.

When that setting is false, registration, resend-verification, and password-reset requests return HTTP 503 EMAIL_DELIVERY_UNAVAILABLE. This is intentional: accepting accounts that can never receive a verification or reset message would be broken behavior. A real transactional-email adapter must be added before a production deployment.

Never enable action-token exposure in a deployed environment.

## Prerequisites on this Mac

The project is locked to Java 17 even if newer JDKs are installed:

~~~zsh
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
~~~

Expected local toolchain:

- Java 17.0.12
- Maven 3.10.1
- MySQL 8.4.13 through Homebrew
- Node.js 20.19+ and npm 10+ for the React application

The Maven Enforcer plugin rejects Java versions outside 17.x. That is deliberate.

## Local configuration

Copy backend/.env.example to backend/.env only if it does not already exist. Populate it with the local MySQL application and Flyway credentials. Do not commit backend/.env.

For this development phase, the required non-database settings are:

~~~properties
TRUSTLAYER_JWT_SECRET=base64-encoded-random-secret-at-least-32-bytes
# Use a separate high-entropy value in deployment. Local development may omit this setting and
# falls back to the JWT secret solely for backwards-compatible startup.
TRUSTLAYER_INDICATOR_HASH_SECRET=separate-high-entropy-random-secret
TRUSTLAYER_ACCESS_TOKEN_TTL=PT15M
TRUSTLAYER_REFRESH_TOKEN_TTL=P7D
TRUSTLAYER_COOKIE_SECURE=false
TRUSTLAYER_EXPOSE_ACTION_TOKENS=true
TRUSTLAYER_THREAT_MAX_REPORTS_PER_MINUTE=10
~~~

The Phase 11 extension is disabled unless you explicitly load an unpacked extension and configure
its exact generated identity. Do **not** invent or commit an extension ID. After loading it in
Chrome, copy the exact callback and origin displayed in its options page into the ignored
`backend/.env` as described in [docs/browser-extension.md](docs/browser-extension.md):

~~~properties
TRUSTLAYER_EXTENSION_ENABLED=true
TRUSTLAYER_EXTENSION_CLIENT_ID=trustlayer-chrome-extension
TRUSTLAYER_EXTENSION_REDIRECT_URI=https://your-extension-id.chromiumapp.org/trustlayer
TRUSTLAYER_EXTENSION_ORIGIN=chrome-extension://your-extension-id
~~~

Generate a development JWT secret without putting it in shell history:

~~~zsh
openssl rand -base64 64
~~~

For a deployment, cookies must be Secure, HTTPS must terminate safely, action-token exposure must be false, and a real email-delivery adapter must be configured. The loopback JDBC URL in the local env file deliberately disables TLS only for localhost development; it is not a production configuration.

## Start the backend

~~~zsh
cd backend
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
~~~

The service starts on http://localhost:8080. If that port is already in use:

~~~zsh
SERVER_PORT=8081 ./mvnw spring-boot:run
~~~

Stop a running server with Ctrl+C. Startup validates the Flyway history, applies any pending immutable migrations, and then asks Hibernate only to validate the schema. Hibernate is never allowed to alter the database schema.

## Start the frontend

Keep the backend running, then open a second terminal:

~~~zsh
cd frontend
cp .env.example .env
npm install
npm run dev
~~~

Open http://localhost:5173. During local development Vite proxies `/api` to the backend target in
`frontend/.env`, so browser requests remain same-origin. Never put a secret in a `VITE_*`
variable: Vite publishes those values into the browser bundle.

## Test before running

Run the ordinary isolated suite:

~~~zsh
cd backend
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
~~~

Run the actual MySQL schema and entity-validation test explicitly:

~~~zsh
TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw clean verify
~~~

The real-MySQL command is intentionally opt-in. It expects the local MySQL service and backend/.env to exist.

## Fast manual check

With the service running:

~~~zsh
curl --include http://localhost:8080/api/health
curl --include http://localhost:8080/actuator/health
~~~

The public application health response is:

~~~json
{
  "success": true,
  "message": "TRUSTLAYER API is operational.",
  "data": {
    "status": "UP",
    "service": "trustlayer-backend"
  },
  "timestamp": "runtime-generated ISO-8601 timestamp"
}
~~~

For the complete account sequence, import postman/TRUSTLAYER-Phase-3-Authentication.postman_collection.json into Postman and follow the collection order. The Phase 3 documentation explains the required development token setting and cookie behavior.

## Current project structure

~~~text
.
├── backend/
│   ├── .mvn/                                  Maven Wrapper support
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/trustlayer/
│   │   │   │   ├── config/                    Spring and HTTP security configuration
│   │   │   │   ├── analyzer/                  Static URL rules; no outbound URL inspection
│   │   │   │   ├── controller/                REST endpoint controllers
│   │   │   │   ├── dto/                       Request and response contracts
│   │   │   │   ├── entity/                    JPA database mappings
│   │   │   │   ├── exception/                 Structured API errors
│   │   │   │   ├── repository/                Spring Data repositories
│   │   │   │   ├── security/                  JWT, CSRF, and principals
│   │   │   │   ├── service/                   Account, audit, scanner, and rate-limit logic
│   │   │   │   └── TrustLayerApplication.java
│   │   │   └── resources/
│   │   │       ├── application.yml            Safe runtime configuration
│   │   │       └── db/migration/              Immutable Flyway migrations
│   │   ├── src/test/                          H2 and opt-in MySQL tests
│   │   ├── .env.example                       Tracked configuration template
│   │   ├── mvnw                               Maven wrapper for macOS/Linux
│   │   └── pom.xml                            Dependencies and build restrictions
├── frontend/
│   ├── src/
│   │   ├── api/                               Axios API contracts and auth recovery
│   │   ├── components/                        Reusable accessible UI and dashboard widgets
│   │   ├── context/                           Authentication, theme, and toast state
│   │   ├── layouts/                           Responsive public, auth, and app shells
│   │   ├── pages/                             Routed working product views
│   │   └── test/                              Vitest setup and browser-facing tests
│   ├── .env.example                           Public development proxy configuration
│   └── package.json                           React, Vite, Tailwind, and test scripts
├── browser-extension/                         Manifest V3 URL-scanning client
│   ├── src/                                   Background worker, popup, options, shared policy
│   ├── tests/                                 Node policy/configuration tests
│   └── manifest.json                          Minimal-permission extension manifest
├── docs/
│   ├── authentication.md                      Phase 3 API and security guide
│   ├── database.md                            Schema, indexes, roles, retention
│   ├── url-scanner.md                         Phase 4 API, rules, and security boundary
│   ├── qr-scanner.md                          Phase 6 local QR scan contract
│   ├── transaction-risk.md                    Phase 7 simulation contract
│   ├── threat-intelligence.md                 Phase 8 community-report contract
│   ├── frontend-dashboard.md                  Phase 9 UI and dashboard contract
│   ├── frontend-integration.md                Phase 10 browser/API contract and verification
│   ├── browser-extension.md                   Phase 11 extension boundary and setup
│   ├── ui-accessibility.md                    Phase 12 UX/accessibility implementation notes
│   ├── security-assessment.md                 Phase 13 security regression assessment
│   ├── quality-testing.md                     Phase 14 quality and browser verification
│   ├── architecture.md                        Runtime and trust-boundary design
│   ├── deployment.md                          Docker, production, and rollback guide
│   ├── final-demo.md                          Judge-safe live demonstration script
│   └── final-testing-report.md                Executed checks and release gaps
├── docker/nginx/default.conf                  Frontend route/CSP/API proxy configuration
├── .env.example                               Docker Compose configuration template
├── docker-compose.yml                         Loopback-only local demo stack
└── postman/
    ├── TRUSTLAYER-Phase-1.postman_collection.json
    ├── TRUSTLAYER-Phase-3-Authentication.postman_collection.json
    ├── TRUSTLAYER-Phase-4-URL-Scanner.postman_collection.json
    ├── TRUSTLAYER-Phase-6-QR-Scanner.postman_collection.json
    ├── TRUSTLAYER-Phase-7-Transaction-Risk.postman_collection.json
    └── TRUSTLAYER-Phase-8-Threat-Intelligence.postman_collection.json
~~~

## Database status

The authoritative schema is not a hand-maintained schema.sql file. It is the ordered Flyway migrations:

1. backend/src/main/resources/db/migration/V1__create_trustlayer_schema.sql
2. backend/src/main/resources/db/migration/V2__add_authentication_tokens.sql
3. backend/src/main/resources/db/migration/V3__add_transaction_confidence_level.sql
4. backend/src/main/resources/db/migration/V4__add_extension_authorization_codes.sql

V2 adds password_changed_at to users plus email_verification_tokens and password_reset_tokens. V3 adds transaction confidence-level storage for Phase 7. V4 adds one-time PKCE-bound extension authorization-code hashes. Neither rewrites an already-applied migration. Phases 4 and 6 use scan tables that were deliberately included in V1.

See [docs/database.md](docs/database.md) for tables, relationships, indexes, database roles, retention guidance, and the local TLS limitation.

## Current security limits

These are known limits, not hidden caveats:

- Email is development-token based only. Production registration and recovery fail closed until a real mail adapter exists.
- Login throttling is in process memory. It works for one application instance only; a shared rate limiter such as Redis or an API gateway is required before horizontal scaling.
- URL scan submission throttling is also in process memory (30/user/minute by default). It is a useful single-instance control, not distributed abuse protection.
- URL results are static structural assessments only. There is no live reputation, DNS, redirect, certificate, WHOIS, content, or external threat-intelligence inspection yet. A LOW score is not a safety guarantee.
- Community intelligence is a privacy-redacted report store, not a live feed or automatic fraud verdict. Repeated reports are candidates only; a database-backed human admin role is required to change verification status.
- MFA, user-controlled notification settings, personal-data export, admin role management, and formal data-retention jobs are later work.
- The React app uses a Vite same-origin development proxy. A production frontend must be served from the API origin or use a deliberate credentialed CORS allowlist; wildcard credentialed CORS is not acceptable.
- The Chrome extension has deterministic policy tests and backend authorization integration coverage, but it has not been loaded, connected, browser-channel-tested, or published through the Chrome Web Store in this workspace. Do not claim it protects a real browser until that manual installation flow succeeds.
- Dockerfiles and Compose YAML parse, but Docker was unavailable locally. No container image build, Compose startup, Nginx runtime check, or Docker health-check result exists yet.
- The attempted Java dependency CVE scan could not retrieve its upstream feeds and produced no report. A zero-result frontend or extension npm audit does not prove the Java dependency tree is clean.
- This has not received an independent penetration test, threat-model review, production TLS configuration review, or backup/recovery drill. It must not be called production-ready.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| TRUSTLAYER must be built with Java 17 | Maven picked Java 21 or 27. | Export the Java 17 JAVA_HOME and PATH values above. |
| Port 8080 is already in use | Another process owns the port. | Stop it or use SERVER_PORT=8081. |
| Connection refused or Communications link failure | MySQL 8.4 is not running. | Run brew services restart mysql@8.4. |
| Access denied for database user | backend/.env is absent or credentials do not match the local database accounts. | Correct the ignored local env file; never put passwords in source code. |
| Flyway validation failure | An immutable migration was edited after application. | Restore it and create a new versioned migration for the correction. |
| EMAIL_DELIVERY_UNAVAILABLE | Action-token exposure is disabled and no email adapter exists. | For local testing only set TRUSTLAYER_EXPOSE_ACTION_TOKENS=true; do not use that setting in production. |
| 403 ACCESS_DENIED during a browser mutation | The CSRF header/cookie pair is absent, stale, or wrong. | The React client refreshes the CSRF pair immediately before each mutation. For a manual API client, call GET /api/auth/csrf, retain its XSRF-TOKEN cookie, and send the returned token in X-XSRF-TOKEN. |
| 403 ACCESS_DENIED on POST /api/scanner/url from an older local browser tab | The tab may still be serving old development assets that did not refresh the CSRF pair. | Reload the app or restart `npm run dev`; do not disable CSRF protection. |
| 429 SCAN_RATE_LIMITED | The authenticated user exceeded the configured rolling scan limit. | Wait for the minute window to roll; do not remove the safeguard. |
| 409 DUPLICATE_REPORT | The same account reported the same canonical community indicator twice. | Do not resubmit it; a separate reporter may provide independent corroboration. |
| 429 THREAT_REPORT_RATE_LIMITED | The community report allowance was exceeded. | Wait for the rolling minute window; do not remove the abuse control. |
| 401 after logout, password reset, session revocation, or account deletion | The database-backed session was revoked. | This is the intended immediate invalidation behavior; sign in again if appropriate. |

## Verified implementation status

On 2026-11-17, the application started against local MySQL 8.4.13 after Flyway validated schema
versions 1 through 4. A live browser workflow verified sign-in, a protected URL scan through the
Vite proxy, explainable redacted storage, and a dashboard update sourced from persistent database
records. The isolated backend run passed 42 tests with zero failures and zero errors; its one skip
is the deliberately opt-in real-MySQL test. The opt-in MySQL run passed all 42 tests with zero
failures, zero errors, and zero skips. The extension policy suite passed 3 tests plus static
JavaScript checks. The frontend suite passed 14 tests, its production build succeeded, and both
full and production-only frontend npm audits reported zero vulnerabilities.

The exact commands, completed results, failures, and missing release gates are recorded in
[docs/final-testing-report.md](docs/final-testing-report.md). The extension still has not been
manually loaded or connected in Chrome, and this remains neither an independent security assessment
nor a production certification.
