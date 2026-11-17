package com.trustlayer.service;

import com.trustlayer.analyzer.UrlSafetyAnalyzer;
import com.trustlayer.entity.enums.ThreatType;
import com.trustlayer.exception.ApiException;
import com.trustlayer.security.PrivacyHashService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converts a community submission into a keyed-hash canonical value and a safe display value.
 * Raw indicator values are never placed in a threat response or report record.
 */
@Component
public class ThreatIndicatorCanonicalizer {

    private final UrlSafetyAnalyzer urlSafetyAnalyzer;
    private final PrivacyHashService privacyHashService;

    public ThreatIndicatorCanonicalizer(UrlSafetyAnalyzer urlSafetyAnalyzer, PrivacyHashService privacyHashService) {
        this.urlSafetyAnalyzer = urlSafetyAnalyzer;
        this.privacyHashService = privacyHashService;
    }

    public CanonicalThreatIndicator canonicalize(ThreatType type, String submittedValue) {
        if (type == null || submittedValue == null) {
            throw invalidIndicator();
        }
        return switch (type) {
            case URL -> canonicalUrl(submittedValue);
            case DOMAIN -> canonicalDomain(submittedValue);
            case PHONE -> canonicalPhone(submittedValue);
            case QR_PAYLOAD -> canonicalSensitiveText(submittedValue, "QR payload (redacted)");
            case BENEFICIARY -> canonicalSensitiveText(submittedValue, "Beneficiary identifier (redacted)");
            case OTHER -> canonicalSensitiveText(submittedValue, "Community indicator (redacted)");
        };
    }

    public String sanitizeDescription(String description, ThreatType indicatorType, String submittedIndicatorValue) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(description, Normalizer.Form.NFKC).trim();
        if (normalized.length() > 4_000 || normalized.codePoints().anyMatch(this::unsafeDescriptionCharacter)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_DESCRIPTION",
                    "description contains unsupported characters or exceeds the limit.");
        }
        // Descriptions are not returned by the public API. Remove markup characters as an
        // additional defense for a future moderator UI, then redact the submitted value before
        // storage. This prevents a user from accidentally defeating the indicator redaction by
        // pasting the same URL, phone number, QR payload, or beneficiary value into free text.
        String sanitized = normalized.replace('<', ' ').replace('>', ' ').trim();
        sanitized = redactLiteral(sanitized, submittedIndicatorValue);
        if (indicatorType == ThreatType.URL) {
            sanitized = sanitized.replaceAll("(?i)https?://\\S+", "[redacted URL]");
        }
        if (indicatorType == ThreatType.PHONE) {
            sanitized = sanitized.replaceAll(
                    "(?<![\\p{Alnum}])\\+?\\d(?:[\\s().-]*\\d){6,14}(?![\\p{Alnum}])",
                    "[redacted phone number]"
            );
        }
        return sanitized;
    }

    private CanonicalThreatIndicator canonicalUrl(String submittedValue) {
        try {
            String safeDisplay = urlSafetyAnalyzer.analyze(submittedValue).targetUrl();
            return new CanonicalThreatIndicator(safeDisplay, privacyHashService.indicatorHash(safeDisplay));
        } catch (ApiException exception) {
            throw invalidIndicator();
        }
    }

    private CanonicalThreatIndicator canonicalDomain(String submittedValue) {
        String value = normalizedText(submittedValue).toLowerCase(Locale.ROOT);
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank() || value.length() > 253 || value.contains("/") || value.contains("@") || value.contains(":")) {
            throw invalidIndicator();
        }
        String ascii;
        try {
            ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw invalidIndicator();
        }
        if (!ascii.contains(".") || !ascii.matches("(?i)^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")) {
            throw invalidIndicator();
        }
        return new CanonicalThreatIndicator(ascii, privacyHashService.indicatorHash(ascii));
    }

    private CanonicalThreatIndicator canonicalPhone(String submittedValue) {
        String value = normalizedText(submittedValue).replaceAll("[\\s().-]", "");
        if (!value.matches("^\\+?[0-9]{7,15}$")) {
            throw invalidIndicator();
        }
        String digits = value.startsWith("+") ? value.substring(1) : value;
        String safeDisplay = "Phone number ending " + digits.substring(digits.length() - 4);
        return new CanonicalThreatIndicator(safeDisplay, privacyHashService.indicatorHash(value));
    }

    private CanonicalThreatIndicator canonicalSensitiveText(String submittedValue, String safeDisplay) {
        String normalized = normalizedText(submittedValue);
        if (normalized.length() > 2_048) {
            throw invalidIndicator();
        }
        return new CanonicalThreatIndicator(safeDisplay, privacyHashService.indicatorHash(normalized));
    }

    private String normalizedText(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        if (normalized.isBlank() || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidIndicator();
        }
        return normalized;
    }

    private boolean unsafeDescriptionCharacter(int codePoint) {
        return Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t';
    }

    private String redactLiteral(String description, String submittedValue) {
        if (submittedValue == null || submittedValue.isBlank()) {
            return description;
        }
        String normalizedIndicator = Normalizer.normalize(submittedValue, Normalizer.Form.NFKC).trim();
        if (normalizedIndicator.isBlank()) {
            return description;
        }
        return Pattern.compile(Pattern.quote(normalizedIndicator), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(description)
                .replaceAll("[redacted reported indicator]");
    }

    private ApiException invalidIndicator() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_THREAT_INDICATOR",
                "indicatorValue is invalid for the selected indicator type.");
    }

    public record CanonicalThreatIndicator(String safeDisplayValue, String indicatorHash) {
    }
}
