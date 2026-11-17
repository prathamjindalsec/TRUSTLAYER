# TRUSTLAYER Phase 9 — React application and security dashboard

## What this phase delivers

Phase 9 adds a functional React 18 application in `frontend/` and an authenticated backend
dashboard contract at `GET /api/dashboard/statistics`.

This is not a cosmetic mock-up. The dashboard obtains its values from persisted backend records:

- URL and QR totals, review-recommended counts, risk distribution, and recent scan activity are
  scoped to the authenticated account.
- Transaction counts and risk distribution describe **simulations only**, never real payment
  activity.
- Canonical community-indicator and report counts are explicitly marked as platform-wide. They
  do not establish that every reported item is fraudulent.
- Timeline values are privacy-safe display values that were redacted before persistence. The UI
  never receives raw URL credentials, query values, QR payloads, beneficiary identifiers, or
  private report content.

## Architecture

```text
Browser
  │  React + React Router + Tailwind
  │  access token held only in JavaScript memory
  │  X-XSRF-TOKEN request header for unsafe methods
  ▼
Vite development proxy (/api)
  │  same-origin browser requests in local development
  ▼
Spring Boot API
  │  bearer access token + database-backed session validation
  │  HttpOnly refresh cookie + CSRF protection
  ▼
MySQL / Flyway-managed schema
```

The Vite proxy is intentionally a development-only convenience. It lets the browser make
same-origin `/api` requests without enabling a broad credentialed CORS policy. A later production
deployment must either serve the built application and API from one origin or configure a strict,
explicit origin allowlist.

## Start the application locally

Use two terminals.

Terminal 1 starts the backend:

```zsh
cd /Users/prathamjindal/Documents/Codex/2026-11-16/trustlayer-ultimate-master-development-prompt-ai/backend
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

Terminal 2 starts the frontend:

```zsh
cd /Users/prathamjindal/Documents/Codex/2026-11-16/trustlayer-ultimate-master-development-prompt-ai/frontend
cp .env.example .env
npm install
npm run dev
```

Open `http://localhost:5173` in a browser. Vite is configured with `strictPort: true`; if port
5173 is occupied it fails clearly instead of silently choosing a different port.

If an existing machine-level npm cache has incorrect ownership and `npm install` fails with
`EPERM`, do **not** use `sudo` casually. Use a project-local cache while the ownership issue is
fixed deliberately:

```zsh
npm install --cache ../work/npm-cache
```

Do not place credentials, JWT secrets, database passwords, or service API keys in `.env` files
under `frontend/`: every `VITE_*` value is bundled into browser-visible JavaScript.

## Dashboard endpoint

`GET /api/dashboard/statistics` requires a valid bearer access token. It returns `401` without one.

Example response shape (counts are runtime data, not fixed examples):

```json
{
  "success": true,
  "message": "Dashboard statistics retrieved from current backend data.",
  "data": {
    "totalUrlScans": 4,
    "reviewRecommendedUrlScans": 2,
    "totalQrScans": 1,
    "reviewRecommendedQrScans": 0,
    "totalSimulatedTransactions": 2,
    "reviewRecommendedTransactions": 1,
    "totalCommunityThreats": 12,
    "totalCommunityReports": 30,
    "riskDistribution": [
      { "riskClassification": "LOW", "count": 2 },
      { "riskClassification": "MEDIUM", "count": 1 },
      { "riskClassification": "HIGH", "count": 3 },
      { "riskClassification": "CRITICAL", "count": 1 }
    ],
    "recentActivity": [],
    "personalDataScope": "...",
    "limitation": "..."
  }
}
```

`reviewRecommended*` includes `MEDIUM`, `HIGH`, and `CRITICAL` classifications. It means a
user should inspect the assessment; it is not a count of confirmed attacks or blocked payments.

## User experience and accessibility controls

- Public pages, authentication pages, protected dashboard pages, scanners, simulations, community
  intelligence, and profile/session controls use actual existing API operations.
- The responsive sidebar becomes an explicit dismissible mobile navigation drawer.
- Light and dark themes respect the system setting. Theme persistence occurs only after functional
  preference storage is accepted through the cookie choices panel.
- Cookie choices distinguish essential security storage, functional preferences, and analytics.
  No analytics implementation is enabled in this phase.
- Every operation has visible loading, empty, success, or failure feedback. Buttons are tied to
  real requests rather than decorative placeholders.
- Dialogs have `role="dialog"`, a label, Escape handling, focus placement, focus trapping, and
  focus restoration. Focus-visible styling and reduced-motion support are global.
- Tables retain a horizontal scroll container on narrow screens rather than forcing the page to
  overflow horizontally.

## Authentication flow in the browser

The frontend keeps the short-lived access JWT in memory only. A reload restores a session by using
the backend refresh endpoint; the refresh token is an HttpOnly cookie and is not readable by React.
Before every cookie-authenticated mutation, the client requests a current CSRF pair from
`GET /api/auth/csrf`, retains the returned token as a fallback for privacy-restricted browser
contexts, and sends it in `X-XSRF-TOKEN`. In normal same-origin browsers Axios also reads the
backend-issued `XSRF-TOKEN` cookie. This is deliberate: Spring Security can rotate the readable
CSRF token while processing an authenticated request, so a token obtained only at sign-in is not
reliable for later mutations. The backend remains the authority for authorization, session
revocation, and CSRF validation.

## Quality checks

```zsh
cd frontend
npm test
npm run build
```

The frontend tests cover utility fallbacks/risk ordering, cookie-consent persistence, accessible
modal focus behavior, StrictMode-safe session restoration, and fresh-CSRF wrapping for URL and QR
mutations. The backend `DashboardControllerIntegrationTest` exercises the complete HTTP flow:
sign-in, CSRF, persisted URL scan, transaction simulation, community report, owner scoping, and
absence of raw private inputs from the dashboard response.

## Known limits at this checkpoint

- The dashboard is a read model, not a real-time streaming system. It refreshes when the page
  loads or a user retries after an error.
- The frontend does not replace backend security controls. A user can alter browser JavaScript, so
  all authorization and validation remain enforced by Spring Boot.
- Bundle code splitting is enabled for routes, but performance still requires production browser
  measurement on real devices in Phase 14.
- No PWA/offline queue, i18n, push notifications, production CORS deployment, or independent
  accessibility audit is claimed by this phase. The separately scoped Phase 11 extension is
  documented in `docs/browser-extension.md`.
