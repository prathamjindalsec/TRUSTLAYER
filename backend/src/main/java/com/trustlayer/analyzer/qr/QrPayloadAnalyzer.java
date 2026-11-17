package com.trustlayer.analyzer.qr;

import com.trustlayer.analyzer.UrlAnalysis;
import com.trustlayer.analyzer.UrlSafetyAnalyzer;
import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskIndicatorType;
import com.trustlayer.risk.RiskScoreResult;
import com.trustlayer.risk.RiskScoringEngine;
import com.trustlayer.risk.RiskScoringRequest;
import com.trustlayer.risk.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts a decoded QR payload into an explainable, privacy-safe analysis. This class has no
 * HTTP client, QR payment SDK, browser launcher, or external provider call. It classifies text
 * locally and delegates HTTP(S) destinations to the existing static-only URL analyzer.
 */
@Component
public class QrPayloadAnalyzer {

    public static final String ANALYZER_VERSION = "qr-local-payload-rules-v1";

    private static final Set<String> PAYMENT_SCHEMES = Set.of(
            "bitcoin", "ethereum", "lightning", "paytmmp", "phonepe", "tez", "gpay"
    );
    private static final Set<String> ACTION_SCHEMES = Set.of("tel", "sms", "mailto", "intent");

    private final UrlSafetyAnalyzer urlSafetyAnalyzer;
    private final RiskScoringEngine riskScoringEngine;

    public QrPayloadAnalyzer(UrlSafetyAnalyzer urlSafetyAnalyzer, RiskScoringEngine riskScoringEngine) {
        this.urlSafetyAnalyzer = urlSafetyAnalyzer;
        this.riskScoringEngine = riskScoringEngine;
    }

    public QrPayloadAnalysis analyze(DecodedQrPayload decoded) {
        String payload = decoded.payload().strip();
        Optional<URI> parsedUri = parseUri(payload);
        String scheme = parsedUri.map(URI::getScheme)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);

        if (isWebUrl(parsedUri, scheme)) {
            return analyzeUrl(payload, decoded);
        }
        if ("upi".equals(scheme)) {
            return analyzeUpiPayment(parsedUri.orElseThrow(), decoded);
        }
        if (scheme != null && PAYMENT_SCHEMES.contains(scheme)) {
            return analyzeGenericPaymentUri(scheme, decoded);
        }
        if (scheme != null && ACTION_SCHEMES.contains(scheme)) {
            return analyzeActionUri(scheme, decoded);
        }
        return analyzeText(payload, decoded);
    }

    private QrPayloadAnalysis analyzeUrl(String payload, DecodedQrPayload decoded) {
        UrlAnalysis url = urlSafetyAnalyzer.analyze(payload);
        List<String> recommendations = appendUnique(url.recommendations(),
                "Verify the QR code source and the displayed destination before entering information or approving a payment.");
        List<String> limitations = appendUnique(url.limitations(),
                "The QR image was decoded locally; TRUSTLAYER did not open the decoded destination.");
        Map<String, Object> explanation = new LinkedHashMap<>(url.explanation());
        explanation.put("analysisMode", "LOCAL_QR_IMAGE_AND_STATIC_URL_STRUCTURE");
        explanation.put("qrPayloadType", QrPayloadType.URL.name());
        explanation.put("qrImageFormat", decoded.imageFormat());
        explanation.put("qrImageWidth", decoded.imageWidth());
        explanation.put("qrImageHeight", decoded.imageHeight());
        explanation.put("decodedPayloadStored", false);
        explanation.put("externalActionInitiated", false);
        explanation.put("recommendations", recommendations);
        explanation.put("limitations", limitations);

        return new QrPayloadAnalysis(
                QrPayloadType.URL,
                url.targetUrl(),
                url.riskScore(),
                url.riskClassification(),
                url.confidenceLevel(),
                url.indicators(),
                recommendations,
                limitations,
                explanation,
                ANALYZER_VERSION + "+" + url.analyzerVersion(),
                decoded.imageFormat(),
                decoded.imageWidth(),
                decoded.imageHeight()
        );
    }

    private QrPayloadAnalysis analyzeUpiPayment(URI uri, DecodedQrPayload decoded) {
        Set<String> fields = queryFields(uri.getRawQuery());
        Map<String, Object> paymentFieldPresence = new LinkedHashMap<>();
        paymentFieldPresence.put("hasPayeeAddress", fields.contains("pa"));
        paymentFieldPresence.put("hasPayeeName", fields.contains("pn"));
        paymentFieldPresence.put("hasAmount", fields.contains("am"));
        paymentFieldPresence.put("hasCurrency", fields.contains("cu"));
        paymentFieldPresence.put("hasTransactionReference", hasAny(fields, "tr", "tid", "txnref"));

        List<RiskSignal> signals = new ArrayList<>();
        add(signals, "PAYMENT_REQUEST_ENCODED_IN_QR", "Payment request encoded in QR", RiskIndicatorType.UNKNOWN,
                IndicatorSeverity.INFO, 0, Map.of("payloadType", QrPayloadType.UPI_PAYMENT.name()));
        if (!fields.contains("pa")) {
            add(signals, "UPI_PAYEE_IDENTIFIER_MISSING", "UPI request has no payee identifier",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.MEDIUM, 16,
                    Map.of("payeeAddressPresent", false));
        }
        if (fields.contains("am")) {
            add(signals, "PAYMENT_AMOUNT_PRESENT", "Payment request specifies an amount", RiskIndicatorType.UNKNOWN,
                    IndicatorSeverity.INFO, 0, Map.of("amountPresent", true));
        }

        List<String> limitations = List.of(
                "The QR image was decoded locally; TRUSTLAYER did not start a payment or open an external app.",
                "No bank, UPI, payment-provider, beneficiary, account, or transaction verification was performed.",
                "Recipient, amount, note, and transaction-reference values are intentionally not stored or returned.",
                "A QR code alone cannot establish that a payment request is legitimate."
        );
        List<String> recommendations = List.of(
                "Verify the beneficiary identifier directly in your payment app before authorizing any payment.",
                "Do not approve a collect or payment request solely because it came from a QR code.",
                "If the request was unsolicited or pressures you to act quickly, verify it through an independent channel."
        );
        return scoredAnalysis(
                QrPayloadType.UPI_PAYMENT,
                "UPI payment request (recipient and payment details redacted)",
                signals,
                recommendations,
                limitations,
                Map.of("paymentFieldPresence", paymentFieldPresence),
                decoded
        );
    }

    private QrPayloadAnalysis analyzeGenericPaymentUri(String scheme, DecodedQrPayload decoded) {
        List<RiskSignal> signals = List.of(signal(
                "UNVERIFIED_PAYMENT_URI",
                "Unverified payment URI encoded in QR",
                RiskIndicatorType.UNKNOWN,
                IndicatorSeverity.INFO,
                0,
                Map.of("uriScheme", scheme)
        ));
        List<String> limitations = List.of(
                "The QR image was decoded locally; TRUSTLAYER did not start a payment or open an external app.",
                "No payment-provider, beneficiary, wallet, or transaction verification was performed.",
                "Payment destination and amount values are intentionally not stored or returned."
        );
        List<String> recommendations = List.of(
                "Inspect the payment recipient inside a trusted payment application before authorizing anything.",
                "Treat an unsolicited payment QR code as unverified until you confirm it independently."
        );
        return scoredAnalysis(
                QrPayloadType.PAYMENT_URI,
                "Payment URI (destination details redacted)",
                signals,
                recommendations,
                limitations,
                Map.of("paymentUriScheme", scheme),
                decoded
        );
    }

    private QrPayloadAnalysis analyzeActionUri(String scheme, DecodedQrPayload decoded) {
        List<RiskSignal> signals = List.of(signal(
                "ACTIONABLE_NON_WEB_URI",
                "QR requests an external device action",
                RiskIndicatorType.SUSPICIOUS,
                IndicatorSeverity.LOW,
                8,
                Map.of("uriScheme", scheme)
        ));
        List<String> limitations = List.of(
                "The QR image was decoded locally; TRUSTLAYER did not place a call, send a message, launch an app, or open a destination.",
                "The encoded destination is intentionally not stored or returned."
        );
        List<String> recommendations = List.of(
                "Do not allow an unfamiliar QR code to trigger a call, message, or installed-app action until you verify its source.",
                "Use independently known contact details rather than the QR-provided destination."
        );
        return scoredAnalysis(
                QrPayloadType.ACTION_URI,
                "External-action URI (destination redacted)",
                signals,
                recommendations,
                limitations,
                Map.of("actionUriScheme", scheme),
                decoded
        );
    }

    private QrPayloadAnalysis analyzeText(String payload, DecodedQrPayload decoded) {
        List<RiskSignal> signals = new ArrayList<>();
        add(signals, "QR_TEXT_PAYLOAD", "Text payload encoded in QR", RiskIndicatorType.UNKNOWN,
                IndicatorSeverity.INFO, 0, Map.of("characterCount", payload.codePointCount(0, payload.length())));

        List<String> categories = socialEngineeringCategories(payload);
        if (!categories.isEmpty()) {
            add(signals, "QR_SOCIAL_ENGINEERING_LANGUAGE", "Contains financial-pressure or verification language",
                    RiskIndicatorType.SUSPICIOUS, IndicatorSeverity.LOW, 10,
                    Map.of("matchedCategories", categories));
        }
        List<String> limitations = List.of(
                "The QR image was decoded locally; TRUSTLAYER did not open a destination or execute the text.",
                "Text content is intentionally not stored or returned, so this is not semantic fraud verification.",
                "No external reputation or threat-intelligence source was queried."
        );
        List<String> recommendations = List.of(
                "Treat unsolicited QR content as unverified and confirm any financial request through an independent channel.",
                "Do not share passwords, one-time codes, card data, or payment approvals because a QR payload asks for them."
        );
        return scoredAnalysis(
                QrPayloadType.TEXT,
                "QR text payload (content redacted)",
                signals,
                recommendations,
                limitations,
                Map.of("decodedCharacterCount", payload.codePointCount(0, payload.length())),
                decoded
        );
    }

    private QrPayloadAnalysis scoredAnalysis(
            QrPayloadType type,
            String safeTarget,
            List<RiskSignal> signals,
            List<String> recommendations,
            List<String> limitations,
            Map<String, Object> moduleEvidence,
            DecodedQrPayload decoded
    ) {
        RiskScoreResult score = riskScoringEngine.score(new RiskScoringRequest(
                "QR_LOCAL_PAYLOAD",
                signals,
                limitations,
                false
        ));
        Map<String, Object> explanation = new LinkedHashMap<>(score.explanation());
        explanation.put("analysisMode", "LOCAL_QR_IMAGE_AND_PAYLOAD_CLASSIFICATION");
        explanation.put("qrPayloadType", type.name());
        explanation.put("qrImageFormat", decoded.imageFormat());
        explanation.put("qrImageWidth", decoded.imageWidth());
        explanation.put("qrImageHeight", decoded.imageHeight());
        explanation.put("decodedPayloadStored", false);
        explanation.put("externalActionInitiated", false);
        explanation.put("recommendations", recommendations);
        explanation.put("limitations", limitations);
        explanation.putAll(moduleEvidence);

        return new QrPayloadAnalysis(
                type,
                safeTarget,
                score.riskScore(),
                score.riskClassification(),
                score.confidenceLevel(),
                signals,
                recommendations,
                limitations,
                explanation,
                ANALYZER_VERSION,
                decoded.imageFormat(),
                decoded.imageWidth(),
                decoded.imageHeight()
        );
    }

    private Optional<URI> parseUri(String payload) {
        try {
            return Optional.of(new URI(payload));
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private boolean isWebUrl(Optional<URI> parsedUri, String scheme) {
        return parsedUri.isPresent()
                && ("http".equals(scheme) || "https".equals(scheme))
                && parsedUri.get().isAbsolute()
                && parsedUri.get().getRawAuthority() != null;
    }

    private Set<String> queryFields(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Set.of();
        }
        Set<String> fields = new LinkedHashSet<>();
        for (String pair : rawQuery.split("&")) {
            String rawKey = pair.substring(0, pair.indexOf('=') >= 0 ? pair.indexOf('=') : pair.length());
            try {
                String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (key.matches("[a-z0-9_]{1,32}")) {
                    fields.add(key);
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed field is simply not used as evidence; no raw input is exposed.
            }
        }
        return Set.copyOf(fields);
    }

    private boolean hasAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> socialEngineeringCategories(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> categories = new ArrayList<>();
        if (containsAny(normalized, "urgent", "immediately", "act now", "limited time")) {
            categories.add("URGENCY");
        }
        if (containsAny(normalized, "verify", "kyc", "password", "otp", "one-time code")) {
            categories.add("CREDENTIAL_OR_IDENTITY");
        }
        if (containsAny(normalized, "payment", "pay", "refund", "bank", "wallet", "beneficiary")) {
            categories.add("FINANCIAL_ACTION");
        }
        return List.copyOf(categories);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> appendUnique(List<String> current, String additional) {
        LinkedHashSet<String> values = new LinkedHashSet<>(current);
        values.add(additional);
        return List.copyOf(values);
    }

    private void add(
            List<RiskSignal> signals,
            String code,
            String name,
            RiskIndicatorType type,
            IndicatorSeverity severity,
            int weight,
            Map<String, Object> evidence
    ) {
        signals.add(signal(code, name, type, severity, weight, evidence));
    }

    private RiskSignal signal(
            String code,
            String name,
            RiskIndicatorType type,
            IndicatorSeverity severity,
            int weight,
            Map<String, Object> evidence
    ) {
        return new RiskSignal(code, name, type, severity, BigDecimal.valueOf(weight), evidence);
    }
}
