# TRUSTLAYER hackathon demonstration

## Demonstration goal

Show a simple, honest story: a person receives a risky destination, TRUSTLAYER explains the
signals before the person acts, and the platform keeps a privacy-reduced record for that user's
dashboard. Do not say that TRUSTLAYER blocks money transfers, validates real bank activity, or
proves a site safe. None of those claims are supported by the implementation.

## Recommended live scenario

### 1. Start with the user risk

Show a fictional payment request that directs the user to this safe-to-demo reserved-address URL:

```text
http://paypal.com@198.51.100.99:8080/secure-login?redirect=https%3A%2F%2Fexample.test
```

`198.51.100.99` belongs to the documentation-only TEST-NET-2 block, and TRUSTLAYER never opens
the destination. The input is deliberately structured to demonstrate static indicators such as
HTTP, user-info obfuscation, a direct IP host, a non-standard port, credential/financial keywords,
and a redirect-style parameter.

### 2. Scan the URL

1. Sign in to the React app with a prepared local demo account.
2. Open **URL Scanner** and paste the safe-to-demo URL above.
3. Submit it once.
4. Show the score, classification, named indicators, individual weights, confidence level,
   recommendations, and stated limitations.
5. Explicitly point out that the result comes from static local rules. It did not make an outbound
   connection and it does not prove that the target is fraudulent.

Expected narrative: the platform makes the reasoning inspectable instead of displaying an
unexplained artificial-intelligence score.

### 3. Show the dashboard uses live data

Navigate to **Dashboard**. Show that the URL scan appears in the user's recent activity and that
the aggregate values come from persisted backend data. Do not describe any number as a global
fraud statistic or a real-world threat count.

### 4. Show QR safety without executing a payment

Upload a pre-prepared QR image that contains a known HTTP(S) demonstration URL. Show that the
backend decodes locally, identifies the payload type, runs the same static URL analysis when the
payload is an HTTP(S) URL, and returns a warning/report. Never scan a real payment QR code during
a stage demo and never click an extracted destination from the application.

### 5. Show a simulated transaction assessment

Open **Transaction Risk** and enter a high nominal amount, a new fictional beneficiary identifier,
and an overnight local time. Explain that the feature assesses only user-entered demo data and
same-user simulation history. It does not contact a bank, UPI provider, card network, wallet, or
payment rail.

### 6. Show community intelligence and verification status

Submit one fictional suspicious URL or phone-number report. Show that the result is stored as
`UNVERIFIED`, deduplicated for the same reporting user, and separately searchable. Explain that
repeated submissions may be candidates for review but never become a confirmed fraud verdict by
themselves.

## Optional extension segment

Only demonstrate the browser extension if a human has loaded it in Chrome, entered its exact
extension callback/origin into `backend/.env`, connected it through the PKCE consent flow, and
completed a manual check in the current environment.

Until that has happened, show the extension source and architecture diagram instead. Claiming that
it protects browsing before the manual installation flow succeeds would be false.

## Presenter script in plain language

> “This link contains multiple static risk signals. TRUSTLAYER shows each signal and its weight,
> then records a redacted result for this signed-in user. The system did not visit the destination,
> so it avoids turning URL analysis into a server-side request risk. The result is decision support,
> not a guarantee and not a payment block.”

> “The transaction screen is a simulation. It compares user-entered behavior against earlier demo
> records for that same account. It does not touch a bank or payment network.”

## Demonstration preparation checklist

- Start MySQL and the backend, then verify `/api/health`.
- Start the frontend and verify sign-in with the prepared demo account.
- Clear or label test data if the audience should see a clean dashboard.
- Keep a prepared QR demonstration image locally, with a non-production URL only.
- Ensure the presenter knows the login credentials without displaying them on screen.
- Have the static URL ready to paste, rather than visiting it in a browser.
- Disable any untested extension segment rather than improvising it on stage.
- Keep `docs/security-assessment.md` and `docs/final-testing-report.md` available for judges who
  ask what has and has not been verified.
