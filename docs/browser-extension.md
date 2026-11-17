# TRUSTLAYER Phase 11 — Chrome extension

## What exists

`browser-extension/` is a Chrome Extension Manifest V3 implementation, not a pretend browser mock-up.

- A popup performs user-initiated checks of the active public HTTP(S) page.
- Optional automatic checking runs only after a user grants site permission and enables it in settings.
- High and critical results can show an in-page warning and optional desktop notification.
- The popup renders classification, score, explainable indicators, and a recommended next step.
- Extension options contain exact backend/web-app origins and user-controlled protection settings.

The extension does **not** open destinations, read form fields, inspect passwords, copy browser cookies, block navigation, or block, send, approve, reverse, or delay a financial transaction.

## Authorization architecture

The web app's access JWT and HttpOnly refresh cookie never enter extension storage. Putting either credential in `chrome.storage.local` would turn an extension compromise into an account compromise.

```text
Popup → background service worker → Chrome identity web-auth window
                                      |
                                      v
                          React /extension/connect consent page
                                      |
             authenticated browser JWT + fresh CSRF token (one explicit approval)
                                      |
                                      v
                  POST /api/extension/authorizations (Spring Boot)
                                      |
                         one-time PKCE authorization code
                                      |
                                      v
   chrome.identity callback → POST /api/extension/token + PKCE verifier
                                      |
                                      v
              five-minute URL-scan-only bearer token in chrome.storage.session
```

The backend stores only the SHA-256 digest of the authorization code. It requires an exact client ID, exact `https://<extension-id>.chromiumapp.org/trustlayer` callback URI, exact `chrome-extension://<extension-id>` origin, S256 PKCE, one-time code use within one minute, and an active underlying database-backed user session.

The token has a separate `extension_url_scan` use, an extension audience, a `url:scan` scope, and a five-minute lifetime. It can call only `POST /api/extension/scan-url`. The security filter rejects it when presented to ordinary browser endpoints; logout, session revocation, password reset, and account deletion invalidate it because each request checks the parent session.

## API contract

These endpoints are for the extension flow. They are deliberately not a replacement for normal web
authentication, and a raw browser access token cannot be substituted for the scoped token.

### `POST /api/extension/authorizations`

The React consent page calls this after an authenticated user presses **Allow URL-scan protection**.
It requires a normal browser `Authorization: Bearer <access-token>` header and the current
`X-XSRF-TOKEN` cookie/header pair. It accepts only the exact server-configured extension client and
callback URI.

```json
{
  "clientId": "trustlayer-chrome-extension",
  "redirectUri": "https://your-extension-id.chromiumapp.org/trustlayer",
  "codeChallenge": "base64url-sha256-of-a-pkce-verifier",
  "codeChallengeMethod": "S256",
  "state": "extension-generated-unpredictable-state"
}
```

It returns `201 Created` with a one-time code, matching callback, state, `url:scan` scope, and an
expiry timestamp. The React page immediately sends that code to the HTTPS Chrome callback using
`window.location.replace`; it does not place it in local storage or browser history. `400` means an
input/client mismatch, `401` means the browser session is not valid, `403` means a non-browser
token attempted consent, and `503 EXTENSION_UNAVAILABLE` means the operator has not configured the
exact extension identity.

### `POST /api/extension/token`

This stateless exchange is called from the service worker after Chrome receives the callback. It
does not use cookies or CSRF because it is protected by the single-use code and S256 PKCE verifier.

```json
{
  "clientId": "trustlayer-chrome-extension",
  "redirectUri": "https://your-extension-id.chromiumapp.org/trustlayer",
  "code": "one-time-authorization-code",
  "codeVerifier": "original-high-entropy-pkce-verifier"
}
```

On `200 OK`, the response contains `accessToken`, `tokenType: "Bearer"`, `scope: "url:scan"`, and
`expiresAt`. The extension places it only in `chrome.storage.session`. Code replay, expiry, wrong
client/callback, invalid verifier, logged-out parent session, or a revoked account produces
`401 INVALID_EXTENSION_AUTHORIZATION`.

### `POST /api/extension/scan-url`

The service worker supplies `Authorization: Bearer <extension-url-scan-token>` and no cookies:

```json
{
  "url": "https://suspicious.example/path"
}
```

It returns `201 Created` with the same explainable, privacy-redacted URL-analysis response shape
as the ordinary scanner. Before this request, the extension removes URL credentials, query values,
and fragments and rejects private or browser-local destinations. The backend independently applies
its static analysis, persistence redaction, rate limit, and session validation. A normal web token
is insufficient for this endpoint (`403`), and the scoped extension token is rejected from ordinary
browser API routes (`401`).

## Server setup

1. Start the backend and load the unpacked extension in Chrome from `browser-extension/`.
2. Open **Extension options** and copy the shown callback URI and extension origin.
3. Add their exact values to ignored `backend/.env`:

```properties
TRUSTLAYER_EXTENSION_ENABLED=true
TRUSTLAYER_EXTENSION_CLIENT_ID=trustlayer-chrome-extension
TRUSTLAYER_EXTENSION_REDIRECT_URI=https://your-extension-id.chromiumapp.org/trustlayer
TRUSTLAYER_EXTENSION_ORIGIN=chrome-extension://your-extension-id
TRUSTLAYER_EXTENSION_AUTHORIZATION_CODE_TTL=PT1M
TRUSTLAYER_EXTENSION_ACCESS_TOKEN_TTL=PT5M
```

4. Restart Spring Boot so the exact CORS allowlist and extension configuration load.
5. In extension options, use `http://localhost:8080` for the local API and `http://localhost:5173` for the local web app. Save, grant the requested backend permission, then use **Connect securely**.
6. Sign in in the identity window and explicitly approve URL-scan protection. The extension stores only the resulting short-lived token in `chrome.storage.session`, not on disk.

Do not configure wildcard extension origins, wildcard Chrome callback URIs, or a production HTTP origin. They undermine the authorization boundary.

## Permissions

| Permission | Why it exists | Default effect |
| --- | --- | --- |
| `activeTab` | Lets a user manually inspect the page they deliberately opened the popup for. | No permanent all-site access. |
| `storage` | Stores non-secret settings locally and the access token in session-only storage. | Token clears on browser restart. |
| `identity` | Runs the PKCE web-authorization window. | No password is handled by extension code. |
| `tabs` | Detects completed navigation only after automatic checks are enabled. | No history is read or stored. |
| `scripting` | Injects a warning only after a high/critical result. | No always-on content script. |
| `notifications` | Optional high/critical automatic-scan alerts. | No low/medium notification by default. |
| optional site access | Needed for automatic HTTPS scanning or the configured backend. | Requested only from settings. |

## Privacy behavior

Before a page is submitted, the extension strips URL username/password components, query values, and fragments. It rejects browser-internal, `file:`, `localhost`, `.local`, loopback, link-local, and RFC 1918 private-network pages. The backend independently redacts sensitive URL elements before persistence and never contacts a scanned destination.

Removing query values is an intentional privacy tradeoff. It can reduce static-rule coverage for a query-only lure, but it avoids automatically transmitting session tokens, reset tokens, or personal data that happen to appear in an address bar.

## Test and load

```zsh
cd browser-extension
npm test
```

Open `chrome://extensions`, enable **Developer mode**, select **Load unpacked**, and choose `browser-extension`. The test suite covers public URL policy, private-address rejection, privacy sanitization, and backend-origin validation. Backend integration coverage is in `ExtensionAuthorizationIntegrationTest`.

## Coverage limits

- The extension analyzes URLs only; it does not decode QR images from arbitrary pages.
- Automatic checks are opt-in, run after supported HTTPS navigation, are best effort, and are constrained by the backend account rate limit.
- A warning means static rules found signals. It is not proof of fraud or proof that a site is unsafe. A low score is not a safety guarantee.
- Closing the popup does not revoke the short-lived token; use **Disconnect extension**, revoke the web session, or wait up to five minutes.
- This has not been published to the Chrome Web Store or tested across Chrome channels. Packaging, store review, and independent extension-security review remain production tasks.
