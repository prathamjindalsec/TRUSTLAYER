package com.trustlayer.analyzer;

import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.RiskIndicatorType;
import com.trustlayer.exception.ApiException;
import com.trustlayer.risk.RiskScoreResult;
import com.trustlayer.risk.RiskScoringEngine;
import com.trustlayer.risk.RiskScoringRequest;
import com.trustlayer.risk.RiskSignal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, static URL analysis only.
 *
 * <p>This component intentionally contains no HTTP client, DNS resolver, certificate check,
 * WHOIS client, redirect follower, or threat-intelligence call. Analysing a user-supplied URL
 * must not become a server-side request forgery primitive. The rules here are structural signals,
 * not proof of malware or fraud.</p>
 */
@Component
public class UrlSafetyAnalyzer {

    public static final String ANALYZER_VERSION = "url-static-rules-v1";

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");
    private static final Set<String> URL_SHORTENERS = Set.of(
            "bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "rebrand.ly", "shorturl.at"
    );
    private static final Set<String> HIGH_ABUSE_TLDS = Set.of(
            "zip", "mov", "country", "gq", "tk", "work", "top", "xyz", "click", "link", "rest", "party", "cam", "sbs"
    );
    private static final Set<String> REDIRECT_PARAMETER_NAMES = Set.of(
            "redirect", "redirect_uri", "redirect_url", "url", "next", "continue", "return", "return_url", "target", "destination"
    );
    private static final Map<String, List<String>> KEYWORD_CATEGORIES = Map.of(
            "CREDENTIAL_COLLECTION", List.of("login", "signin", "sign-in", "password", "verify", "verification", "authenticate"),
            "FINANCIAL_ACTION", List.of("bank", "banking", "wallet", "payment", "upi", "kyc", "card", "beneficiary"),
            "URGENCY_OR_ACCOUNT_PRESSURE", List.of("urgent", "suspended", "unlock", "secure", "security-alert"),
            "CRYPTO_BRAND_TARGETING", List.of("crypto", "airdrop", "seedphrase", "seed-phrase")
    );
    private static final Map<String, Set<String>> BRAND_OFFICIAL_DOMAINS = Map.ofEntries(
            Map.entry("paypal", Set.of("paypal.com")),
            Map.entry("google", Set.of("google.com")),
            Map.entry("microsoft", Set.of("microsoft.com")),
            Map.entry("apple", Set.of("apple.com")),
            Map.entry("amazon", Set.of("amazon.com")),
            Map.entry("coinbase", Set.of("coinbase.com")),
            Map.entry("binance", Set.of("binance.com")),
            Map.entry("phonepe", Set.of("phonepe.com")),
            Map.entry("paytm", Set.of("paytm.com")),
            Map.entry("whatsapp", Set.of("whatsapp.com")),
            Map.entry("gpay", Set.of("pay.google.com"))
    );

    private final RiskScoringEngine riskScoringEngine;

    public UrlSafetyAnalyzer(RiskScoringEngine riskScoringEngine) {
        this.riskScoringEngine = riskScoringEngine;
    }

    public UrlAnalysis analyze(String candidate) {
        ParsedUrl url = parse(candidate);
        List<RiskSignal> indicators = new ArrayList<>();

        if (!"https".equals(url.scheme())) {
            add(indicators, "URL_NOT_HTTPS", "Uses unencrypted HTTP", IndicatorSeverity.MEDIUM, 12,
                    Map.of("scheme", url.scheme()));
        }

        if (url.hasUserInfo()) {
            add(indicators, "URL_USERINFO_OBFUSCATION", "Contains user-info before the host", IndicatorSeverity.HIGH, 20,
                    Map.of("storedTargetRedactsUserInfo", true));
        }

        if (url.ipAddress()) {
            add(indicators, "IP_ADDRESS_HOST", "Uses a direct IP address instead of a domain", IndicatorSeverity.HIGH, 30,
                    Map.of("addressFamily", url.ipv6Literal() ? "IPv6" : "IPv4"));
        }

        if (url.privateOrLocalHost()) {
            add(indicators, "PRIVATE_OR_LOCAL_HOST", "Targets a private, loopback, or local host", IndicatorSeverity.HIGH, 18,
                    Map.of("networkLookupPerformed", false));
        }

        if (url.idnOrPunycodeHost()) {
            add(indicators, "IDN_OR_PUNYCODE_HOST", "Uses an internationalized or punycode host", IndicatorSeverity.HIGH, 20,
                    Map.of("hostEncoding", url.rawHostHasNonAscii() ? "unicode" : "punycode"));
        }

        if (URL_SHORTENERS.contains(url.host())) {
            add(indicators, "URL_SHORTENER", "Uses a URL-shortening service", IndicatorSeverity.MEDIUM, 15,
                    Map.of("domain", url.host()));
        }

        String topLevelDomain = topLevelDomain(url.host());
        if (HIGH_ABUSE_TLDS.contains(topLevelDomain)) {
            add(indicators, "HIGH_ABUSE_TLD", "Uses a frequently abused top-level domain", IndicatorSeverity.MEDIUM, 10,
                    Map.of("topLevelDomain", topLevelDomain));
        }

        List<String> impersonatedBrands = findImpersonatedBrands(url.host());
        if (!impersonatedBrands.isEmpty()) {
            add(indicators, "BRAND_LOOKALIKE", "Potential brand impersonation in the host", IndicatorSeverity.HIGH, 30,
                    Map.of("matchedBrands", impersonatedBrands));
        }

        if (subdomainCount(url.host()) >= 3) {
            add(indicators, "EXCESSIVE_SUBDOMAINS", "Contains an unusually deep subdomain chain", IndicatorSeverity.MEDIUM, 8,
                    Map.of("subdomainCount", subdomainCount(url.host())));
        }

        if (url.nonDefaultPort()) {
            add(indicators, "NON_STANDARD_PORT", "Uses a non-default web port", IndicatorSeverity.MEDIUM, 8,
                    Map.of("port", url.port()));
        }

        int lengthWeight = url.inputLength() > 400 ? 12 : url.inputLength() > 180 ? 6 : 0;
        if (lengthWeight > 0) {
            add(indicators, "LONG_URL", "Contains an unusually long URL", IndicatorSeverity.LOW, lengthWeight,
                    Map.of("length", url.inputLength()));
        }

        int queryParameterCount = queryParameterCount(url.rawQuery());
        if (queryParameterCount >= 6) {
            add(indicators, "MANY_QUERY_PARAMETERS", "Contains many query parameters", IndicatorSeverity.LOW, 6,
                    Map.of("parameterCount", queryParameterCount));
        }

        List<String> redirectParameters = redirectParameters(url.rawQuery());
        if (!redirectParameters.isEmpty()) {
            add(indicators, "REDIRECT_PARAMETER", "Contains a redirect-style query parameter", IndicatorSeverity.MEDIUM, 8,
                    Map.of("matchedParameterNames", redirectParameters));
        }

        List<String> keywordCategories = keywordCategories(url);
        if (!keywordCategories.isEmpty()) {
            int weight = Math.min(12, keywordCategories.size() * 4);
            add(indicators, "SUSPICIOUS_KEYWORD_PATTERN", "Contains credential, financial, or urgency terms", IndicatorSeverity.LOW, weight,
                    Map.of("matchedCategories", keywordCategories));
        }

        if (percentEscapeCount(url) >= 3) {
            add(indicators, "EXCESSIVE_PERCENT_ENCODING", "Uses repeated percent-encoding", IndicatorSeverity.LOW, 6,
                    Map.of("percentEscapeCount", percentEscapeCount(url)));
        }

        if (hasObfuscatedLabel(url.host())) {
            add(indicators, "OBFUSCATED_DOMAIN_LABEL", "Contains a mixed digit or heavily hyphenated host label", IndicatorSeverity.LOW, 6,
                    Map.of("host", url.host()));
        }

        List<String> limitations = limitations();
        RiskScoreResult scoring = riskScoringEngine.score(new RiskScoringRequest(
                "URL_STATIC_STRUCTURE",
                indicators,
                limitations,
                false
        ));
        List<String> recommendations = recommendations(indicators, scoring.riskClassification());

        Map<String, Object> explanation = new LinkedHashMap<>(scoring.explanation());
        explanation.put("analysisMode", "STATIC_LOCAL_URL_STRUCTURE");
        explanation.put("recommendations", recommendations);
        explanation.put("targetStorage", "Credentials, path values, query values, and fragments are redacted before persistence.");

        return new UrlAnalysis(
                url.safeDisplayUrl(),
                scoring.riskScore(),
                scoring.riskClassification(),
                scoring.confidenceLevel(),
                indicators,
                recommendations,
                limitations,
                explanation,
                ANALYZER_VERSION
        );
    }

    private ParsedUrl parse(String candidate) {
        if (candidate == null) {
            throw invalidUrl();
        }

        String input = candidate.trim();
        if (input.isEmpty() || input.length() > 2048 || containsControlCharacter(input)) {
            throw invalidUrl();
        }

        URI uri;
        try {
            uri = new URI(input);
        } catch (URISyntaxException exception) {
            throw invalidUrl();
        }

        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || !SUPPORTED_SCHEMES.contains(scheme) || uri.getRawAuthority() == null) {
            throw invalidUrl();
        }

        Authority authority = parseAuthority(uri.getRawAuthority());
        String rawHost = authority.host();
        boolean rawHostHasNonAscii = rawHost.codePoints().anyMatch(codePoint -> codePoint > 127);
        boolean ipAddress = authority.ipv6Literal() || isIpv4Address(rawHost);
        String host = normalizeHost(rawHost, ipAddress);
        int port = authority.port();
        boolean idnOrPunycodeHost = !ipAddress && (rawHostHasNonAscii || host.contains("xn--"));
        boolean privateOrLocalHost = isPrivateOrLocalHost(host, ipAddress, authority.ipv6Literal());

        String rawPath = uri.getRawPath();
        String rawQuery = uri.getRawQuery();
        String rawFragment = uri.getRawFragment();
        String safeDisplayUrl = safeDisplayUrl(scheme, host, port, authority.ipv6Literal(), rawPath, rawQuery);

        return new ParsedUrl(
                scheme,
                host,
                port,
                ipAddress,
                authority.ipv6Literal(),
                authority.hasUserInfo(),
                rawHostHasNonAscii,
                idnOrPunycodeHost,
                privateOrLocalHost,
                rawPath,
                rawQuery,
                rawFragment,
                input.length(),
                safeDisplayUrl
        );
    }

    private Authority parseAuthority(String rawAuthority) {
        String hostAndPort = rawAuthority;
        int at = rawAuthority.lastIndexOf('@');
        boolean hasUserInfo = at >= 0;
        if (hasUserInfo) {
            hostAndPort = rawAuthority.substring(at + 1);
        }
        if (hostAndPort.isBlank()) {
            throw invalidUrl();
        }

        if (hostAndPort.startsWith("[")) {
            int closingBracket = hostAndPort.indexOf(']');
            if (closingBracket <= 1) {
                throw invalidUrl();
            }
            String host = hostAndPort.substring(1, closingBracket);
            String suffix = hostAndPort.substring(closingBracket + 1);
            int port = suffix.isEmpty() ? -1 : parsePortSuffix(suffix);
            if (!host.matches("[0-9A-Fa-f:.]+")) {
                throw invalidUrl();
            }
            return new Authority(host, port, true, hasUserInfo);
        }

        int firstColon = hostAndPort.indexOf(':');
        int lastColon = hostAndPort.lastIndexOf(':');
        if (firstColon != lastColon) {
            // An IPv6 literal must be bracketed in an HTTP(S) URI. Reject rather than guessing.
            throw invalidUrl();
        }
        if (lastColon >= 0) {
            String host = hostAndPort.substring(0, lastColon);
            String suffix = hostAndPort.substring(lastColon + 1);
            if (host.isBlank() || suffix.isBlank()) {
                throw invalidUrl();
            }
            return new Authority(host, parsePort(suffix), false, hasUserInfo);
        }
        return new Authority(hostAndPort, -1, false, hasUserInfo);
    }

    private int parsePortSuffix(String suffix) {
        if (!suffix.startsWith(":")) {
            throw invalidUrl();
        }
        return parsePort(suffix.substring(1));
    }

    private int parsePort(String value) {
        if (value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            throw invalidUrl();
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw invalidUrl();
            }
            return port;
        } catch (NumberFormatException exception) {
            throw invalidUrl();
        }
    }

    private String normalizeHost(String rawHost, boolean ipAddress) {
        if (rawHost.isBlank() || rawHost.indexOf('%') >= 0 || containsControlCharacter(rawHost)) {
            throw invalidUrl();
        }
        if (ipAddress) {
            return rawHost.toLowerCase(Locale.ROOT);
        }

        String asciiHost;
        try {
            asciiHost = IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw invalidUrl();
        }

        while (asciiHost.endsWith(".")) {
            asciiHost = asciiHost.substring(0, asciiHost.length() - 1);
        }
        if (asciiHost.isBlank() || asciiHost.length() > 253 || asciiHost.contains("..")) {
            throw invalidUrl();
        }
        return asciiHost;
    }

    private String safeDisplayUrl(
            String scheme,
            String host,
            int port,
            boolean ipv6Literal,
            String rawPath,
            String rawQuery
    ) {
        String renderedHost = ipv6Literal ? "[" + host + "]" : host;
        boolean defaultPort = ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        String authority = port < 0 || defaultPort ? renderedHost : renderedHost + ":" + port;
        boolean hasNonRootPath = rawPath != null && !rawPath.isBlank() && !"/".equals(rawPath);
        boolean hasQuery = rawQuery != null && !rawQuery.isBlank();
        return scheme + "://" + authority
                + (hasNonRootPath ? "/[path-redacted]" : "")
                + (hasQuery ? "?[query-redacted]" : "");
    }

    private List<String> findImpersonatedBrands(String host) {
        if (isOfficialBrandHost(host)) {
            return List.of();
        }

        String[] labels = host.split("\\.");
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> brand : BRAND_OFFICIAL_DOMAINS.entrySet()) {
            for (int index = 0; index < Math.max(0, labels.length - 1); index++) {
                String normalizedLabel = labels[index].replaceAll("[^a-z0-9]", "");
                if (looksLikeBrand(normalizedLabel, brand.getKey())) {
                    matches.add(brand.getKey());
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }

    private boolean isOfficialBrandHost(String host) {
        return BRAND_OFFICIAL_DOMAINS.values().stream()
                .flatMap(Set::stream)
                .anyMatch(officialDomain -> host.equals(officialDomain) || host.endsWith("." + officialDomain));
    }

    private boolean looksLikeBrand(String label, String brand) {
        if (label.equals(brand) || label.contains(brand)) {
            return true;
        }
        if (label.length() >= brand.length()) {
            String prefix = label.substring(0, brand.length());
            if (levenshteinDistance(prefix, brand) <= 1) {
                return true;
            }
        }
        return label.length() == brand.length() && levenshteinDistance(label, brand) <= 1;
    }

    private int levenshteinDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int substitution = previous[j - 1] + (first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), substitution);
            }
            int[] temporary = previous;
            previous = current;
            current = temporary;
        }
        return previous[second.length()];
    }

    private List<String> keywordCategories(ParsedUrl url) {
        String material = (url.host() + " "
                + decodeForAnalysis(url.rawPath()) + " "
                + decodeForAnalysis(url.rawQuery()) + " "
                + decodeForAnalysis(url.rawFragment())).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Map.Entry<String, List<String>> category : KEYWORD_CATEGORIES.entrySet()) {
            if (category.getValue().stream().anyMatch(material::contains)) {
                matches.add(category.getKey());
            }
        }
        return List.copyOf(matches);
    }

    private List<String> redirectParameters(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String pair : rawQuery.split("&")) {
            String rawName = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            String name = decodeForAnalysis(rawName).toLowerCase(Locale.ROOT);
            if (REDIRECT_PARAMETER_NAMES.contains(name)) {
                matches.add(name);
            }
        }
        return List.copyOf(matches);
    }

    private int queryParameterCount(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(rawQuery.split("&", -1)).filter(part -> !part.isBlank()).count();
    }

    private int percentEscapeCount(ParsedUrl url) {
        return countPercentEscapes(url.rawPath()) + countPercentEscapes(url.rawQuery()) + countPercentEscapes(url.rawFragment());
    }

    private int countPercentEscapes(String value) {
        if (value == null) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index + 2 < value.length(); index++) {
            if (value.charAt(index) == '%' && isHex(value.charAt(index + 1)) && isHex(value.charAt(index + 2))) {
                count++;
                index += 2;
            }
        }
        return count;
    }

    private boolean isHex(char value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F');
    }

    private boolean hasObfuscatedLabel(String host) {
        String[] labels = host.split("\\.");
        for (int index = 0; index < Math.max(0, labels.length - 1); index++) {
            String label = labels[index];
            long hyphenCount = label.chars().filter(character -> character == '-').count();
            boolean hasLetter = label.chars().anyMatch(Character::isLetter);
            boolean hasDigit = label.chars().anyMatch(Character::isDigit);
            if (hyphenCount >= 2 || (hasLetter && hasDigit)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPrivateOrLocalHost(String host, boolean ipAddress, boolean ipv6Literal) {
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true;
        }
        if (!ipAddress) {
            return false;
        }
        if (ipv6Literal) {
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.equals("::1") || normalized.equals("::")
                    || normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe80");
        }

        int[] octets = ipv4Octets(host);
        int first = octets[0];
        int second = octets[1];
        return first == 0 || first == 10 || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private boolean isIpv4Address(String host) {
        try {
            ipv4Octets(host);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int[] ipv4Octets(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Not IPv4.");
        }
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isEmpty() || !parts[index].chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Not IPv4.");
            }
            try {
                octets[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Not IPv4.");
            }
            if (octets[index] < 0 || octets[index] > 255) {
                throw new IllegalArgumentException("Not IPv4.");
            }
        }
        return octets;
    }

    private int subdomainCount(String host) {
        if (host.indexOf(':') >= 0) {
            return 0;
        }
        String[] labels = host.split("\\.");
        return Math.max(0, labels.length - 2);
    }

    private String topLevelDomain(String host) {
        if (host.indexOf(':') >= 0 || !host.contains(".")) {
            return "";
        }
        return host.substring(host.lastIndexOf('.') + 1);
    }

    private List<String> recommendations(
            List<RiskSignal> indicators,
            RiskClassification classification
    ) {
        Set<String> codes = indicators.stream().map(RiskSignal::code).collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<String> recommendations = new ArrayList<>();
        if (classification == RiskClassification.LOW) {
            recommendations.add("No configured static red flags were found. Treat this as absence of evidence, not proof that the destination is safe.");
        } else if (classification == RiskClassification.MEDIUM) {
            recommendations.add("Verify the destination through a known official app, bookmark, or independently typed address before signing in or paying.");
            recommendations.add("Do not provide passwords, one-time passwords, card details, or payment approvals from this link alone.");
        } else {
            recommendations.add("Do not enter credentials, one-time passwords, payment details, or approve a transaction from this destination.");
            recommendations.add("Use a known official app or a trusted bookmark to reach the organisation independently.");
        }
        if (codes.contains("URL_SHORTENER") || codes.contains("REDIRECT_PARAMETER")) {
            recommendations.add("Resolve the final destination through a trusted source before opening the link.");
        }
        if (codes.contains("PRIVATE_OR_LOCAL_HOST")) {
            recommendations.add("Do not expose internal or local network services through an untrusted link.");
        }
        return List.copyOf(recommendations);
    }

    private List<String> limitations() {
        return List.of(
                "Static local URL analysis only; TRUSTLAYER did not connect to this destination.",
                "No DNS, HTTP redirect, TLS certificate, WHOIS, domain-age, reputation, or threat-intelligence lookup was performed.",
                "No indicator is confirmed malicious in this scan because no verified intelligence source was queried.",
                "A LOW score means no configured static rule fired; it is not a safety guarantee."
        );
    }

    private void add(
            List<RiskSignal> indicators,
            String code,
            String name,
            IndicatorSeverity severity,
            int weight,
            Map<String, Object> evidence
    ) {
        indicators.add(new RiskSignal(
                code,
                name,
                RiskIndicatorType.SUSPICIOUS,
                severity,
                BigDecimal.valueOf(weight),
                evidence
        ));
    }

    private String decodeForAnalysis(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private ApiException invalidUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_URL", "Provide a valid absolute HTTP or HTTPS URL.");
    }

    private record Authority(String host, int port, boolean ipv6Literal, boolean hasUserInfo) {
    }

    private record ParsedUrl(
            String scheme,
            String host,
            int port,
            boolean ipAddress,
            boolean ipv6Literal,
            boolean hasUserInfo,
            boolean rawHostHasNonAscii,
            boolean idnOrPunycodeHost,
            boolean privateOrLocalHost,
            String rawPath,
            String rawQuery,
            String rawFragment,
            int inputLength,
            String safeDisplayUrl
    ) {
        boolean nonDefaultPort() {
            return port >= 0 && !(("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80));
        }
    }
}
