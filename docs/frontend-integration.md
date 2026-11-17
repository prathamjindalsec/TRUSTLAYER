# TRUSTLAYER Phase 10 — frontend and backend integration

## Purpose

Phase 10 verifies that the React application is a real client of the Spring Boot API. It does not
introduce a second backend, mock REST responses, or move authorization decisions into the browser.
The browser presents state and user feedback; the Java application remains the source of truth for
validation, authorization, rate limiting, privacy redaction, risk scoring, and persistence.

## Local request path

```text
Browser at http://localhost:5173
        |
        | same-origin /api requests
        v
Vite development proxy
        |
        v
Spring Boot at http://localhost:8080
        |
        v
MySQL / Flyway-managed schema
```

The Vite proxy target is `VITE_BACKEND_TARGET` in `frontend/.env`; it defaults to
`http://localhost:8080`. Its configuration loads that value through Vite's `loadEnv` API, rather
than incorrectly reading Node's `process.env`. This means a copied local `.env` genuinely controls
the proxy target.

No broad CORS policy is enabled for local development. A browser only sees same-origin paths such
as `/api/scanner/url`; Vite performs the local reverse proxy hop. This keeps the browser security
model close to the intended production architecture.

## Authentication and CSRF contract

```text
1. React submits sign-in credentials to POST /api/auth/login.
2. Spring returns a short-lived access JWT in the response body and sets an HttpOnly refresh cookie.
3. React retains only the access JWT in memory.
4. On reload, React calls POST /api/auth/refresh; Java validates and rotates the cookie-backed session.
5. Before a protected mutation, React calls GET /api/auth/csrf.
6. React sends the current token in X-XSRF-TOKEN with its bearer access JWT.
7. Spring validates both the CSRF token and the active database-backed session.
```

The client intentionally asks for a fresh CSRF pair immediately before each mutation. Spring's
CSRF authentication strategy can replace the readable token during an authenticated request, so a
token cached only at sign-in is not reliable. `withFreshCsrf` centralizes that rule for URL/QR
scans, transaction simulations, reports, profile changes, session revocations, and logout.

React StrictMode can replay mount effects in development. `AuthContext` shares an in-flight boot
refresh request, preventing a second concurrent refresh from appearing to replay a rotated refresh
cookie and falsely revoking the user's session.

## API-to-screen mapping

| Browser area | Spring Boot endpoints | Important server guarantee |
| --- | --- | --- |
| Registration, verification, sign-in, recovery | `/api/auth/*` | Password policy, BCrypt hashing, token hashing, rotating session records, and audit logging are server-side. |
| URL scanner | `POST /api/scanner/url`, scanner history/detail endpoints | Submitted URLs are statically inspected only; the server does not fetch them. Stored target details are redacted. |
| QR scanner | `POST /api/scanner/qr`, QR history/detail endpoints | Image and decoded payload limits apply before local-only analysis. No payment action or navigation is executed. |
| Transaction lab | `POST /api/transactions/analyze`, history/detail endpoints | Inputs describe simulations, not real payments. Beneficiary values are privacy-redacted. |
| Threat intelligence | `/api/threats/*` | Community reports remain unverified until an authorized reviewer changes their status. |
| Dashboard | `GET /api/dashboard/statistics` | Personal metrics are account-scoped; community metrics are explicitly platform-wide. |
| Profile and sessions | `/api/users/*` | Ownership, password confirmation, and active-session checks are enforced by Java. |

All client API modules consume the same envelope: successful payloads are read from `data.data`,
and failures become an accessible page-level or form-level message. The frontend does not treat a
displayed risk score as a direct fraud verdict or a payment-blocking decision.

## Manual verification sequence

Run the backend and frontend in separate terminals:

```zsh
cd backend
./mvnw spring-boot:run
```

```zsh
cd frontend
npm run dev
```

Then verify the following in `http://localhost:5173`:

1. Register and verify a development account, then sign in.
2. Submit a clearly synthetic suspicious URL such as
   `https://paypa1-demo.example.zip/auth?session=demo`.
3. Confirm that the assessment appears with explainable evidence and a redacted persisted target.
4. Return to the dashboard and confirm that the URL-assessment count and recent activity changed.
5. Refresh the page and confirm that a valid session restores without a second sign-in.
6. Sign out and confirm that a protected route redirects to sign-in.

Do not use an account password, card number, OTP, or real banking URL as test data.

## Verified result on 2026-11-17

A live local browser run created a URL assessment through the protected React flow and received a
`46/100` medium explainable result. The stored URL path and query were redacted. The dashboard then
read the persisted database state and showed three account-scoped URL assessments, including the
new activity. This run exercised the Vite proxy, authentication recovery, current CSRF flow,
backend risk scoring, persistence, and dashboard aggregation together.

This is not a production deployment test. It did not test a public reverse proxy, HTTPS, CDN,
multi-instance rate limiting, an independent browser matrix, or an external penetration test.

## Production boundary

Production should serve the static React build and proxy `/api` from one HTTPS origin, for example:

```text
https://app.example.com/       -> static React build
https://app.example.com/api/*  -> Spring Boot private upstream
```

That model preserves `SameSite=Strict` refresh cookies and avoids broad credentialed CORS. If a
separate frontend origin is deliberately chosen later, it requires an explicit, reviewed origin
allowlist, `allowCredentials=true`, strict `Vary: Origin` behavior, CSRF testing, and a reassessment
of cookie SameSite behavior. `Access-Control-Allow-Origin: *` with credentials is both invalid and
unsafe.
