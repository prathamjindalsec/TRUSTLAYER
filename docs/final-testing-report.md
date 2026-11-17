# TRUSTLAYER final testing report

**Report date:** 2026-11-17
**Build status:** Hackathon MVP verified locally. Not production-ready.

## Executed checks

| Area | Command or method | Result |
| --- | --- | --- |
| Java toolchain and backend suite | `./mvnw -ntp -Dmaven.repo.local=../work/m2 verify` with Java 17.0.12 | Passed: 42 tests, 0 failures, 0 errors, 1 skipped real-MySQL test. |
| MySQL schema and backend suite | `TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw -ntp -Dmaven.repo.local=../work/m2 verify` | Passed: 42 tests, 0 failures, 0 errors, 0 skips. Flyway validated 4 migrations against local MySQL 8.4.13. |
| Production safety guard | Included in the backend suite | Passed: 3 tests cover accepted safe configuration plus rejected insecure cookies, exposed action tokens, duplicated privacy/JWT secret, and a too-short decoded JWT secret. |
| HTTP hardening | Included in the backend suite | Passed: API CSP, frame denial, referrer policy, permissions policy, `nosniff`, structured unauthenticated response, and malformed JSON response checks. |
| Frontend unit and accessibility checks | `npm test -- --run` | Passed: 8 files and 14 tests. Includes an axe structural accessibility fixture. |
| Frontend production build | `npm run build` | Passed: Vite 6.4.6 built 2,082 modules. Shared main JavaScript was 96.04 kB gzip and the lazy dashboard route was 114.26 kB gzip. |
| Frontend dependency audit | `npm audit --audit-level=moderate` and `npm audit --omit=dev --audit-level=moderate` | Passed: 0 reported vulnerabilities. |
| Extension policy tests | `npm test` | Passed: 3 tests, 0 failures. |
| Extension JavaScript syntax | `node --check` over every `browser-extension/src/*.js` source file | Passed: no syntax error. |
| Extension dependency audit | `npm audit --audit-level=moderate` | Passed: 0 reported vulnerabilities. |
| Responsive browser check | Local Chromium inspection at 390×844 and 1440×900 | Passed: no measured horizontal overflow; mobile nav opened, closed on Escape, and preserved trigger focus; the skip link moved focus to main content; checked landing-route console had no errors. |
| Secret-pattern sweep | Targeted `rg` scan excluding ignored `.env`, build, and dependency paths | Passed: no matches for the scanned AWS, GitHub, OpenAI-style, or private-key patterns. This is not a complete secret-management audit. |
| Docker/YAML syntax | Ruby YAML parser over Compose and Spring profile YAML | Passed: syntax parsed. |

## Failed or unavailable checks

| Area | Status | Reason and required next action |
| --- | --- | --- |
| Java dependency CVE scan | Not completed | OWASP Dependency-Check started but could not download its NVD/CISA feeds because of upstream DNS/feed failures. It produced no usable report. Rerun with a maintained cache and NVD API key or approved SCA service. |
| Docker build and Compose runtime | Not executed | The `docker` executable and Docker Desktop engine were unavailable on this Mac. Install/start Docker Desktop, run the deployment guide, and record health-check results. |
| Chrome extension connection | Not executed | The extension source, policy tests, and backend PKCE integration tests pass, but it has not been loaded, granted permissions, connected, or manually checked in Chrome. |
| Safari, Firefox, Edge, Android, iOS testing | Not executed | Only a local Chromium-based browser check ran. Test real devices, touch input, browser zoom, and assistive technologies before public release. |
| Lighthouse/Web Vitals | Not executed | A successful Vite build reports bundle size only. It does not measure LCP, INP, CLS, or field performance. |
| Authenticated OWASP ZAP scan | Not executed | Requires a disposable deployed target and explicit authenticated test account. |
| Load, stress, backup/restore, and failover tests | Not executed | No production-like infrastructure exists in this workspace. |

## Known limits and unresolved risks

- URL and QR analysis are static and local. There is no DNS, HTTP fetch, redirect, certificate,
  WHOIS, live reputation, or external threat-feed check. A LOW score does not establish safety.
- Transaction analysis is a simulation only. It cannot authorize, block, delay, reverse, or view a
  real bank, UPI, card, wallet, or payment-provider transaction.
- The email workflow has no production mail adapter. With action-token exposure disabled it fails
  closed, which is correct but blocks production registration/recovery.
- Login and analyzer rate limits are in-memory and work only for a single backend instance.
- The real MySQL test validates the developer's local schema. It is not proof of a fresh hosted
  database deployment, encrypted backup restoration, or least-privilege production credentials.
- The Compose configuration is a local loopback demo. It has no public TLS termination, HSTS,
  secret manager, centralized logs, monitoring, or a tested incident response process.

## Conclusion

The local source code is in a strong position for a hackathon demonstration: core user journeys,
explainable analysis, persistence, authorization boundaries, and regression tests run. It does not
meet the original prompt's full production-ready standard. Calling it production-ready now would
be inaccurate because the deployment, independent security review, Java SCA report, extension
manual test, cross-browser/device matrix, email delivery, distributed controls, and operational
recovery work remain incomplete.
