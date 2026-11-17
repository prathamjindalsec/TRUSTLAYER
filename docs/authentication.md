# TRUSTLAYER Phase 3 — authentication, sessions, and account API

## Purpose and boundary

Phase 3 turns the Phase 1–2 backend into a real account system. It is not a decorative login screen and it does not accept a password without creating a secure, revocable server-side session.

This document covers the authentication endpoints implemented in Phase 3. URL, QR, and simulated transaction APIs were added in later phases; see their dedicated documents. Threat intelligence, dashboard, frontend, and browser-extension endpoints still must not be assumed to exist until their own phases are completed.

## Authentication model

~~~text
Browser or API client
        │
        │ POST /api/auth/login with email and password
        ▼
AuthenticationService
        │
        ├─ BCrypt verifies password
        ├─ creates database-backed authentication_sessions row
        ├─ returns a short-lived signed access JWT in JSON
        └─ sets a long-lived rotating refresh JWT in an HttpOnly cookie

Protected request
        │ Authorization: Bearer access-token
        ▼
JWT filter
        │
        ├─ verifies signature, issuer, expiry, and token type
        ├─ loads the current database user
        └─ verifies the JWT's originating session is still active
        ▼
Controller and service
~~~

The session check is deliberate. It means these operations invalidate an already-issued access token immediately:

- individual-session revocation;
- normal logout;
- logout from all devices;
- password reset;
- soft account deletion.

The trade-off is one efficient indexed database session-existence query for every protected API request. That is a reasonable security-first choice at this stage. A later production performance review can add carefully bounded caching without weakening revocation guarantees.

## Token and cookie rules

| Artifact | Where it lives | Default lifetime | Purpose | Important rule |
| --- | --- | --- | --- | --- |
| Access token | JSON login or refresh response | 15 minutes | Authorization header for protected API calls | Keep it in application memory, not localStorage. |
| Refresh token | trustlayer_refresh HttpOnly cookie scoped to /api/auth | 7 days | Obtain a new access token | Never expose it to client-side JavaScript. |
| CSRF token | XSRF-TOKEN readable cookie and GET /api/auth/csrf response | Session browser cookie lifetime | Proves a browser mutation was initiated by the trusted client | Send its exact value in X-XSRF-TOKEN for protected state-changing requests. |
| Verification/reset token | Development response only when explicitly enabled | 24 hours / 30 minutes | Verify address or set a new password | Raw values are not stored in MySQL. |

Refresh tokens rotate after each successful refresh. Presenting an old refresh token after rotation is treated as token reuse and revokes the session.

The session list reports lastSeenAt. To avoid a database write on every protected request, the backend advances this value at most once every five minutes while the session remains active.

Refresh-token rotation and consumption of verification or password-reset tokens take a database row lock inside their transaction. That prevents two simultaneous requests from both treating the same one-time value as unused.

## Configuration

The backend reads local secrets from the ignored backend/.env file. CI and deployed environments should provide the same values through their platform secret manager or environment.

| Variable | Local-development value | Meaning |
| --- | --- | --- |
| TRUSTLAYER_JWT_SECRET | Base64 random value, at least 32 decoded bytes | HMAC signing secret for access and refresh JWTs. |
| TRUSTLAYER_ACCESS_TOKEN_TTL | PT15M | ISO-8601 duration for access JWT lifetime. |
| TRUSTLAYER_REFRESH_TOKEN_TTL | P7D | ISO-8601 duration for refresh JWT and session lifetime. |
| TRUSTLAYER_COOKIE_SECURE | false locally | Must be true behind production HTTPS. |
| TRUSTLAYER_EXPOSE_ACTION_TOKENS | true locally | Development-only testing switch. Never enable in a deployment. |

When TRUSTLAYER_EXPOSE_ACTION_TOKENS is false, registration, resend verification, and password-reset requests fail with 503 EMAIL_DELIVERY_UNAVAILABLE. That is intentional. Until a real mail provider is built, pretending that an email was delivered would be broken and misleading.

## API response conventions

Successful JSON responses use this envelope:

~~~json
{
  "success": true,
  "message": "Human-readable result",
  "data": {},
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

For an endpoint without response data, data is omitted because the JSON serializer excludes nulls.

Errors use this envelope:

~~~json
{
  "success": false,
  "code": "MACHINE_READABLE_CODE",
  "message": "Safe explanation for the client",
  "fieldErrors": {
    "email": "must be a well-formed email address"
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

The server intentionally does not return stack traces, password hashes, refresh tokens, database credentials, or raw database exceptions.

## Endpoint overview

| Method and path | Authentication | CSRF header | Main success status |
| --- | --- | --- | --- |
| GET /api/auth/csrf | Public | No | 200 |
| POST /api/auth/register | Public | No | 201 |
| POST /api/auth/verify-email | Public | No | 200 |
| POST /api/auth/resend-verification | Public | No | 202 |
| POST /api/auth/login | Public | No | 200 |
| POST /api/auth/refresh | Refresh cookie | Yes | 200 |
| POST /api/auth/logout | Refresh cookie, optional | Yes when cookie is sent | 204 |
| POST /api/auth/logout-all | Access JWT | Yes | 204 |
| POST /api/auth/forgot-password | Public | No | 202 |
| POST /api/auth/reset-password | Public | No | 200 |
| GET /api/users/profile | Access JWT | No | 200 |
| PUT /api/users/profile | Access JWT | Yes | 200 |
| GET /api/users/sessions | Access JWT | No | 200 |
| DELETE /api/users/sessions/{sessionId} | Access JWT | Yes | 204 |
| GET /api/users/login-history | Access JWT | No | 200 |
| DELETE /api/users/account | Access JWT | Yes | 204 |

The access JWT is sent as:

~~~http
Authorization: Bearer <access-token>
~~~

The cookie-protected mutation header is:

~~~http
X-XSRF-TOKEN: <value from GET /api/auth/csrf>
~~~

The browser or Postman cookie jar must retain the XSRF-TOKEN and trustlayer_refresh cookies.

## Endpoint reference and examples

### 1. Obtain a CSRF token

GET /api/auth/csrf

Request body: none.

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "CSRF token issued.",
  "data": {
    "token": "8e3e5b75-...",
    "headerName": "X-XSRF-TOKEN"
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

It also sets an XSRF-TOKEN cookie. Use this endpoint before any browser-originated protected mutation.

### 2. Register an account

POST /api/auth/register

~~~json
{
  "email": "ada@example.test",
  "password": "Secure!Pass123",
  "firstName": "Ada",
  "lastName": "Lovelace"
}
~~~

Success, HTTP 201 in development:

~~~json
{
  "success": true,
  "message": "Registration accepted. Verify the email address before signing in.",
  "data": {
    "user": {
      "id": 42,
      "email": "ada@example.test",
      "firstName": "Ada",
      "lastName": "Lovelace",
      "role": "USER",
      "status": "PENDING_VERIFICATION",
      "createdAt": "2026-11-16T12:00:00Z"
    },
    "developmentActionToken": "opaque-one-time-token"
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

Possible statuses:

- 201 account pending verification;
- 400 malformed fields or a weak password. Passwords need at least 12 characters with upper-case, lower-case, digit, and symbol classes and cannot exceed BCrypt's 72 UTF-8-byte effective input limit;
- 409 EMAIL_ALREADY_REGISTERED;
- 503 EMAIL_DELIVERY_UNAVAILABLE when development token exposure is disabled and no mail adapter is configured.

### 3. Verify an email address

POST /api/auth/verify-email

~~~json
{
  "token": "opaque-one-time-token"
}
~~~

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Email address verified.",
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

Invalid, expired, reused, or already-invalidated tokens return HTTP 400 with INVALID_ACTION_TOKEN.

### 4. Resend verification

POST /api/auth/resend-verification

~~~json
{
  "email": "ada@example.test"
}
~~~

Success, HTTP 202:

~~~json
{
  "success": true,
  "message": "If the account needs verification, a verification message has been queued.",
  "data": {
    "developmentActionToken": "opaque-one-time-token"
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

The public response wording intentionally does not disclose whether a supplied address is registered or already verified. If action-token delivery is disabled, HTTP 503 EMAIL_DELIVERY_UNAVAILABLE is returned for all callers.

### 5. Sign in

POST /api/auth/login

~~~json
{
  "email": "ada@example.test",
  "password": "Secure!Pass123"
}
~~~

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "accessToken": "signed-access-jwt",
    "tokenType": "Bearer",
    "accessTokenExpiresAt": "2026-11-16T12:15:00Z",
    "user": {
      "id": 42,
      "email": "ada@example.test",
      "firstName": "Ada",
      "lastName": "Lovelace",
      "role": "USER",
      "status": "ACTIVE",
      "emailVerifiedAt": "2026-11-16T12:00:00Z",
      "createdAt": "2026-11-16T12:00:00Z"
    }
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

The response additionally sends:

~~~http
Set-Cookie: trustlayer_refresh=<signed-refresh-jwt>; Path=/api/auth; HttpOnly; SameSite=Strict
Set-Cookie: XSRF-TOKEN=<csrf-token>; Path=/; SameSite=Strict
~~~

Possible statuses:

- 200 success;
- 401 INVALID_CREDENTIALS, including an overlong password attempt that would otherwise be truncated by BCrypt;
- 403 EMAIL_NOT_VERIFIED;
- 403 ACCOUNT_UNAVAILABLE;
- 429 LOGIN_TEMPORARILY_BLOCKED after repeated failures.

### 6. Refresh an access token

POST /api/auth/refresh

Request body: none. The client must send its trustlayer_refresh and XSRF-TOKEN cookies plus:

~~~http
X-XSRF-TOKEN: <csrf-token>
~~~

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Access token refreshed.",
  "data": {
    "accessToken": "new-signed-access-jwt",
    "tokenType": "Bearer",
    "accessTokenExpiresAt": "2026-11-16T12:30:00Z",
    "user": {
      "id": 42,
      "email": "ada@example.test",
      "role": "USER",
      "status": "ACTIVE"
    }
  },
  "timestamp": "2026-11-16T12:15:00Z"
}
~~~

The response replaces the refresh cookie. Reusing the old cookie value triggers session revocation and returns HTTP 401 INVALID_REFRESH_TOKEN. With a valid CSRF proof but no refresh cookie, the endpoint returns HTTP 401 REFRESH_TOKEN_MISSING. An absent or incorrect CSRF header returns HTTP 403 ACCESS_DENIED before the controller is reached.

### 7. Log out the current device

POST /api/auth/logout

Request body: none. Send the cookie jar and X-XSRF-TOKEN header.

Success, HTTP 204 with no response body. The server clears the refresh cookie and revokes the matching database session. Logout is intentionally idempotent: an already-expired or missing refresh token still produces 204 after the CSRF check.

### 8. Log out every device

POST /api/auth/logout-all

Request body: none. Send an active access token, the cookie jar, and X-XSRF-TOKEN.

Success, HTTP 204 with no response body. All current sessions belonging to the authenticated user are revoked. The currently held access token becomes unusable immediately because protected requests check session status.

Possible statuses:

- 204 all sessions revoked;
- 401 missing, expired, invalid, revoked, or deleted-user access token;
- 403 missing or invalid CSRF proof.

### 9. Request a password reset

POST /api/auth/forgot-password

~~~json
{
  "email": "ada@example.test"
}
~~~

Success, HTTP 202 in development:

~~~json
{
  "success": true,
  "message": "If an active account exists for this email, a password reset message has been queued.",
  "data": {
    "developmentActionToken": "opaque-one-time-token"
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

The public message is identical for unknown addresses, preventing account enumeration. For an unknown address, developmentActionToken is null. When delivery is not configured, the endpoint returns HTTP 503 EMAIL_DELIVERY_UNAVAILABLE to every caller.

### 10. Reset a password

POST /api/auth/reset-password

~~~json
{
  "token": "opaque-one-time-token",
  "newPassword": "Replaced!Pass456"
}
~~~

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Password reset successful. Sign in again.",
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

A successful reset consumes the token, revokes every session for that account, and clears stale refresh and CSRF cookies from the response browser. Invalid, expired, or consumed tokens return HTTP 400 INVALID_ACTION_TOKEN. A weak replacement password returns HTTP 400 WEAK_PASSWORD.

### 11. Retrieve the current profile

GET /api/users/profile

Request body: none. Send an access JWT.

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Profile retrieved.",
  "data": {
    "id": 42,
    "email": "ada@example.test",
    "firstName": "Ada",
    "lastName": "Lovelace",
    "role": "USER",
    "status": "ACTIVE",
    "emailVerifiedAt": "2026-11-16T12:00:00Z",
    "createdAt": "2026-11-16T11:55:00Z"
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

The route never accepts a user ID. The subject comes only from the verified access token, so a caller cannot retrieve another user profile by changing a URL parameter.

### 12. Update the current profile

PUT /api/users/profile

~~~json
{
  "firstName": "Grace",
  "lastName": "Hopper"
}
~~~

Send an access JWT, retained XSRF-TOKEN cookie, and X-XSRF-TOKEN header.

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Profile updated.",
  "data": {
    "id": 42,
    "email": "ada@example.test",
    "firstName": "Grace",
    "lastName": "Hopper",
    "role": "USER",
    "status": "ACTIVE"
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

Blank names are normalized to null. Names longer than 100 characters return HTTP 400 VALIDATION_FAILED. Missing CSRF proof returns HTTP 403 ACCESS_DENIED.

### 13. List active sessions

GET /api/users/sessions?page=0&size=20

Request body: none. Send an access JWT. Page sizes are capped at 100.

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Active sessions retrieved.",
  "data": {
    "items": [
      {
        "sessionId": "1d648fb3-...",
        "userAgent": "PostmanRuntime/7.43.0",
        "createdAt": "2026-11-16T12:00:00Z",
        "lastSeenAt": "2026-11-16T12:15:00Z",
        "expiresAt": "2026-11-23T12:00:00Z",
        "current": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-11-16T12:15:00Z"
}
~~~

Refresh token values and token hashes are deliberately absent.

### 14. Revoke one active session

DELETE /api/users/sessions/{sessionId}

Request body: none. Send an access JWT, retained XSRF-TOKEN cookie, and X-XSRF-TOKEN header.

Success, HTTP 204 with no response body.

The service searches by both the current user ID and session ID. A valid user cannot revoke another user’s session. A non-existent, expired, revoked, or foreign session returns HTTP 404 SESSION_NOT_FOUND. Revoking the current session invalidates the current access token immediately and clears the browser’s refresh and CSRF cookies.

### 15. View login history

GET /api/users/login-history?page=0&size=20

Request body: none. Send an access JWT.

Success, HTTP 200:

~~~json
{
  "success": true,
  "message": "Login history retrieved.",
  "data": {
    "items": [
      {
        "eventType": "AUTH.LOGIN_SUCCEEDED",
        "outcome": "SUCCESS",
        "maskedSourceAddress": "127.0.0.*",
        "userAgent": "PostmanRuntime/7.43.0",
        "occurredAt": "2026-11-16T12:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "timestamp": "2026-11-16T12:00:00Z"
}
~~~

Only the authenticated user’s successful, failed, and account-state-denied login events are returned. Failed attempts for an unknown email cannot be attributed to a user account and are therefore not visible in this account-scoped history. Source addresses are masked in the public API; full audit data remains restricted to the database for incident response.

### 16. Delete the current account

DELETE /api/users/account

~~~json
{
  "password": "Secure!Pass123"
}
~~~

Send an access JWT, retained XSRF-TOKEN cookie, and X-XSRF-TOKEN header.

Success, HTTP 204 with no response body. This phase uses a soft delete: the user status becomes DELETED, deleted_at is set, all active sessions are revoked, and the current response clears refresh and CSRF cookies. A future privacy and legal-retention phase must define the hard-purge workflow.

Possible statuses:

- 204 deleted;
- 401 INVALID_CREDENTIALS for an incorrect confirmation password;
- 401 for an invalid or revoked access token;
- 403 for a missing or invalid CSRF proof.

## Postman walkthrough

1. Start the backend and ensure backend/.env has TRUSTLAYER_EXPOSE_ACTION_TOKENS=true for local testing.
2. Import postman/TRUSTLAYER-Phase-3-Authentication.postman_collection.json.
3. Set the collection variables baseUrl, email, password, firstName, and lastName. Use a unique email each time.
4. Run Register account. Its test script saves developmentActionToken as verificationToken.
5. Run Verify email.
6. Run Login. Its test script saves accessToken. Postman stores the refresh and CSRF cookies for the same localhost host automatically.
7. Run Get CSRF token. Its test script saves csrfToken.
8. Run Profile, Update profile, Active sessions, and Login history.
9. Run Refresh access token or Logout current device. The cookie jar and X-XSRF-TOKEN header are essential.
10. The password-reset folder is a separate sequence. It saves resetToken, resets the password, then requires a new login.

If Postman does not retain cookies, open its cookie manager and confirm that XSRF-TOKEN is scoped to localhost path / and trustlayer_refresh is scoped to localhost path /api/auth. Do not manually paste a refresh token into frontend code.

## Automated coverage in this phase

AuthenticationIntegrationTest runs with an isolated H2 database and the actual Spring Security filter chain. It verifies:

- registration starts as PENDING_VERIFICATION;
- unverified accounts cannot sign in;
- verification unlocks login;
- access JWT authorization permits only the authenticated user profile;
- a missing CSRF proof is denied;
- a matching cookie/header CSRF proof allows a profile update;
- refresh-token rotation succeeds;
- logout immediately invalidates the associated access token;
- logout from all devices requires an access token and invalidates every session;
- password reset invalidates existing sessions;
- account deletion invalidates existing sessions;
- password reset, current-session revocation, and account deletion clear stale browser refresh cookies;
- active sessions can be listed and an individual session can be revoked;
- login history contains the authenticated user’s audit event;
- passwords longer than BCrypt's effective 72 UTF-8-byte limit are rejected;
- sign-in also rejects an overlong password rather than accepting a value that merely shares the first 72 BCrypt bytes;
- malformed session identifiers return a validation error instead of an internal error.

MysqlSchemaMigrationIntegrationTest is deliberately opt-in and starts a real Spring application context against MySQL. It validates all currently applied Flyway migrations (V1–V3), the current required tables, and Hibernate’s schema mapping.

LoginAttemptServiceTest proves that five failures cause a temporary block and that the block expires after the configured duration.

## Verification performed on 2026-11-16

| Check | Executed result |
| --- | --- |
| Isolated Maven verification | ./mvnw clean verify passed: 12 tests, 0 failures, 0 errors, 1 skipped real-MySQL test. |
| MySQL Maven verification | TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw clean verify passed: 12 tests, 0 failures, 0 errors, 0 skipped. |
| MySQL migration validation | This historical Phase 3 verification was run when V1 and V2 were current. The current test now validates V1–V3; see later-phase verification notes for the latest run. |
| Hibernate schema validation | Real MySQL 8.4.13 application context started successfully with ddl-auto=validate. |
| Live HTTP workflows | Port 8081 verification passed register 201, verify 200, login 200, CSRF 200, profile 200, refresh 200, session revoke 204, revoked token 401, logout-all 204, account deletion 204, and deleted token 401. |

The Java test runs emit a non-failing class-data-sharing warning from test instrumentation. It does not affect the built application.

## Known limitations and required next work

- Build a real, authenticated email-delivery adapter before production registration or password recovery is enabled.
- Replace the single-node in-memory login throttle with a shared rate-limiter before scaling out.
- Add MFA, device notifications, and administrative account lifecycle controls later; do not falsely claim they exist now.
- Define legal retention, personal-data export, and irreversible purge behavior before treating soft deletion as a complete privacy program.
- Perform independent vulnerability testing, production CORS policy design, HTTPS/TLS review, secret-manager setup, monitoring, backup/recovery tests, and threat modelling before any production claim.
