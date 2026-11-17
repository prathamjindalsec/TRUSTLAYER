# TRUSTLAYER Phase 6 — safe QR-code security scanner

## Purpose and strict boundary

Phase 6 adds a working, authenticated QR-image scanner. It decodes a QR payload locally, classifies the kind of payload, reuses the static URL analyzer for HTTP(S) destinations, calculates an explainable risk result, and stores only a privacy-safe result.

It does **not**:

- open a decoded URL, follow redirects, resolve DNS, inspect a certificate, or fetch remote content;
- launch a browser, phone dialer, SMS client, installed application, wallet, UPI app, or payment provider;
- initiate, approve, decline, block, or observe a real financial transaction;
- verify a bank account, UPI beneficiary, wallet, payment URI, or merchant;
- store or return raw decoded QR text, UPI IDs, recipient names, amounts, notes, transaction references, phone numbers, or arbitrary action-URI destinations.

That is not a cosmetic restriction. Automatically acting on QR payloads would create a dangerous execution path and would make unsupported claims about financial integrations.

## Architecture

```text
Authenticated client + XSRF proof
        |
        | multipart/form-data: image (PNG or JPEG, <= 5 MB)
        v
QrScannerController
        |
        v
QrScannerService ----> per-user/per-scan-type in-memory throttle
        |
        +--> QrImageDecoder
        |      - verifies PNG/JPEG magic bytes
        |      - validates dimensions before full pixel decoding
        |      - decodes only QR_CODE with ZXing, entirely in memory
        |
        +--> QrPayloadAnalyzer
        |      - HTTP(S): UrlSafetyAnalyzer (static, no network)
        |      - UPI/payment/action/text: local type-specific signals only
        |
        +--> shared WeightedRiskScoringEngine
        +--> scans -> scan_results -> risk_indicators
        +--> audit_logs (SCANNER.QR_ANALYZED)
        v
Redacted, owner-scoped JSON response
```

The decoder accepts only a real PNG or JPEG header. It ignores the multipart filename and claimed MIME type. Spring rejects requests above 5 MB; the decoder independently enforces the same byte limit, a maximum 4,096 pixels in either dimension, and a maximum 12,000,000 total pixels before it decodes the image. Upload bytes are never written to disk.

The QR library is ZXing 3.5.4. It is used only for local decoding, never to contact a decoded destination.

## Supported payload categories

| Category | Recognition | Result behavior |
| --- | --- | --- |
| `URL` | Absolute `http` or `https` URI | Delegates to the Phase 4 static URL rules. URL credentials, path values, query values, and fragments remain redacted. |
| `UPI_PAYMENT` | `upi:` URI | Reports only safe field-presence facts (for example whether an amount field exists). Recipient and payment values are redacted. |
| `PAYMENT_URI` | Known non-UPI payment URI scheme such as `bitcoin:` | Marks it as an unverified payment URI; no wallet or payment app is started. |
| `ACTION_URI` | `tel:`, `sms:`, `mailto:`, or `intent:` | Warns that it requests an external device action; destination stays redacted. |
| `TEXT` | Everything else | Reports only payload length and local social-engineering language categories; raw text is not retained. |

An ordinary UPI or payment URI is not called fraud merely because it is a payment request. In the absence of verified intelligence, the Phase 6 engine does not create `CONFIRMED_MALICIOUS` evidence and does not assign high confidence.

## Privacy and persistence

The existing Flyway `scans` table already has a `QR` scan type. No migration was needed. A successful request creates a `QR` scan, one `scan_results` row, its individual `risk_indicators`, and an audit event. It does **not** persist image bytes or raw decoded data.

Safe `target` examples:

```text
UPI payment request (recipient and payment details redacted)
Payment URI (destination details redacted)
External-action URI (destination redacted)
QR text payload (content redacted)
https://example.test/[path-redacted]?[query-redacted]
```

The last example follows the existing URL redaction policy. Evidence JSON contains only fixed codes, boolean field presence, dimensions, a URI scheme where appropriate, or safe counts.

## REST API

All QR routes require a valid bearer access token. The `POST` route also requires the CSRF cookie/header pair because the authentication design includes a browser refresh-token cookie.

### POST `/api/scanner/qr`

Request:

```http
Authorization: Bearer <access-token>
X-XSRF-TOKEN: <csrf-token>
Cookie: XSRF-TOKEN=<csrf-token>
Content-Type: multipart/form-data
```

Form field: `image` — a PNG or JPEG file containing a QR code.

Success: HTTP `201 Created`.

```json
{
  "success": true,
  "message": "QR image decoded and analyzed without opening its destination or initiating a payment.",
  "data": {
    "scanId": 44,
    "target": "UPI payment request (recipient and payment details redacted)",
    "payloadType": "UPI_PAYMENT",
    "imageFormat": "PNG",
    "imageWidth": 320,
    "imageHeight": 320,
    "status": "COMPLETED",
    "riskScore": 0,
    "riskClassification": "LOW",
    "confidenceLevel": "LOW",
    "indicators": [{ "code": "PAYMENT_REQUEST_ENCODED_IN_QR", "type": "UNKNOWN", "weight": 0 }],
    "limitations": ["The QR image was decoded locally; TRUSTLAYER did not start a payment or open an external app."],
    "explanation": {
      "decodedPayloadStored": false,
      "externalActionInitiated": false,
      "riskScoringEngineVersion": "weighted-risk-engine-v1"
    }
  }
}
```

`LOW` means only that the configured local QR rules did not find enough risk weight. It never means the code, destination, beneficiary, or payment is safe.

| Status | Code | Meaning |
| ---: | --- | --- |
| 400 | `QR_IMAGE_REQUIRED` | No `image` form part or an empty upload. |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, expired, revoked, or invalid access token. |
| 403 | `ACCESS_DENIED` | Missing or invalid CSRF proof. |
| 413 | `QR_IMAGE_TOO_LARGE` | Multipart payload or decoder input exceeds 5 MB. |
| 415 | `UNSUPPORTED_QR_IMAGE` | File bytes are neither PNG nor JPEG. |
| 422 | `INVALID_QR_IMAGE` | Bytes do not form a valid supported image. |
| 422 | `QR_IMAGE_DIMENSIONS_UNSUPPORTED` | Image exceeds the decoder's safe dimensions/pixel limit. |
| 422 | `QR_CODE_NOT_FOUND` | Valid image contains no readable QR code. |
| 422 | `QR_PAYLOAD_TOO_LARGE` | Decoded payload exceeds the 4,096-character safety limit. |
| 429 | `SCAN_RATE_LIMITED` | Authenticated user exceeded the configured rolling per-type limit. |

### GET `/api/scanner/qr/history?page=0&size=20`

Returns only the authenticated user's QR scans, newest first. Raw QR content is never present.

### GET `/api/scanner/qr/{scanId}`

Returns the authenticated user's full redacted QR result. A scan owned by somebody else deliberately returns `404 SCAN_NOT_FOUND` rather than revealing its existence.

## Postman sequence

1. Import `postman/TRUSTLAYER-Phase-3-Authentication.postman_collection.json` and sign in, then paste the access token into the Phase 6 collection.
2. Import `postman/TRUSTLAYER-Phase-6-QR-Scanner.postman_collection.json`.
3. Set `qrImagePath` to an absolute path for a harmless test PNG/JPEG QR image. The collection does not manufacture a payment QR for you.
4. Run **Get CSRF token**, then **Scan QR image**, then history and detail.
5. Keep Postman's cookie jar enabled so the matching `XSRF-TOKEN` cookie is preserved.

## Automated verification

`QrScannerIntegrationTest` dynamically generates QR PNGs in memory and proves:

- CSRF rejection before upload analysis;
- actual UPI QR decoding, successful persistence, history, and detail retrieval;
- no raw payee, recipient-name, amount, note, or transaction-reference leakage in the response;
- owner-scoped QR detail access and a 404 for another user;
- reuse of static URL rules for a URL stored in a QR;
- rejection of an unsupported text upload.

Run the isolated suite:

```zsh
cd /Users/prathamjindal/Documents/Codex/2026-11-16/trustlayer-ultimate-master-development-prompt-ai/backend
./mvnw -ntp test
```

Run the opt-in MySQL validation suite only while local MySQL and `backend/.env` are configured:

```zsh
TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS=true ./mvnw -ntp verify
```

## Known limits

- QR decoding supports PNG and JPEG only. It does not support SVG, PDF, WebP, camera capture, or multi-image batch scan workflows.
- QR image analysis is synchronous and local. Heavy/high-volume production scanning needs a queue, isolated worker process, antivirus policy, and distributed rate limit.
- There is no live reputation source, bank/UPI validation, merchant validation, or transaction-provider integration.
- A QR payload can be malicious even when Phase 6 produces `LOW`; the system has no basis to claim that a QR code is safe.
