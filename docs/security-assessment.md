# TRUSTLAYER Phase 13 — security regression assessment

## Scope and result

This is an engineering security-regression assessment, not an independent penetration-test report
or production security certification. It tests the Spring Security chain, existing privacy and
authorization boundaries, browser-extension isolation, source-secret hygiene, and JavaScript
dependency advisories that were available in this development environment.

## Checks that passed

| Area | Evidence | Result |
| --- | --- | --- |
| API security headers | `SecurityHardeningIntegrationTest` checks CSP, frame denial, referrer policy, permissions policy, and `nosniff` on a live MockMvc response. | Passed, 3 assertions-based test cases. |
| Fail-closed access control | The same test proves an unauthenticated dashboard request returns a structured 401 response without private data. Existing scanner/dashboard/account tests cover owner scoping and IDOR resistance. | Passed in the Spring test profile. |
| Malformed input handling | A malformed login JSON body returns the stable `MALFORMED_REQUEST` envelope with no `trace` or `exception` field. | Passed. |
| Browser CSRF boundary | Authentication, scanner, transaction, threat, dashboard, and extension authorization integration tests require the documented fresh CSRF pair for cookie-capable browser mutations. | Covered by existing integration tests. |
| URL/SSRF boundary | URL and QR modules use static parsing/decoding only; they do not resolve, fetch, follow redirects, inspect certificates, or execute payment/QR instructions. The extension additionally rejects browser-local and private-network destinations before submission. | Implemented and regression-tested. |
| Extension credential isolation | `ExtensionAuthorizationIntegrationTest` proves one-time S256 PKCE exchange, exact CORS origin, limited `url:scan` scope, one-time-code rejection, and rejection of an extension token on ordinary scanner routes. | Passed, 2 integration tests. |
| Frontend production dependencies | `npm audit` after React Router 7.18.5 and Vitest 5.1.0 upgrades. | `0 vulnerabilities`. |
| Extension dependencies | The extension has a committed lockfile and no dependencies; `npm audit` and production-only audit both ran. | `0 vulnerabilities`. |
| Source-secret sweep | A targeted scan excluded ignored local `.env` files and build/vendor directories. It found only documented placeholder values in `README.md` and `backend/.env.example`. | No exposed credential found by this limited pattern scan. |

## Dependency remediation performed

The initial frontend audit identified two moderate React Router 6 advisories. There is no fixed
v6 line, so the frontend was moved to `react-router-dom` 7.18.5 after checking its declared Node
and React peer requirements. The test runner was moved from Vitest 3.2.7 to 5.1.0 to remove a
moderate `@vitest/mocker` advisory and two critical `tinypool` advisories. The React test suite and
production build passed after both upgrades.

## Java dependency CVE scan: not completed

An OWASP Dependency-Check 12.1.0 scan was actually started against the backend. It attempted to
download the public NVD/CISA databases without an NVD API key, retried failed requests, and exited
with status 1 after the upstream feeds failed/returned DNS errors. No usable Java vulnerability
report was produced. **Do not treat this as a clean Java dependency scan.**

For CI or release review, run Dependency-Check with a maintained cache plus an NVD API key, or use
an approved SCA service. Gate the build on its report and review suppressions rather than blindly
accepting a zero-findings result.

## Not performed and still required

- An authenticated OWASP ZAP baseline/full scan against a disposable deployed environment.
- Independent manual penetration testing and a formal threat-model review.
- Network egress testing from an isolated production-like environment.
- Production TLS, reverse-proxy, secret-manager, backup/restore, and incident-response drills.
- Chrome extension store review and independent extension-security review.

The controls above materially reduce common risks, but they do not justify a production-ready or
“secure” claim on their own.
