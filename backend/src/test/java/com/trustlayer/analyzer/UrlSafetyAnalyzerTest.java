package com.trustlayer.analyzer;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.exception.ApiException;
import com.trustlayer.risk.RiskSignal;
import com.trustlayer.risk.WeightedRiskScoringEngine;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyAnalyzerTest {

    private final UrlSafetyAnalyzer analyzer = new UrlSafetyAnalyzer(new WeightedRiskScoringEngine());

    @Test
    void ordinaryHttpsUrlHasNoSafetyGuaranteeWhenNoStaticRuleFires() {
        UrlAnalysis analysis = analyzer.analyze("https://www.example.com/products");

        assertThat(analysis.riskScore()).isZero();
        assertThat(analysis.riskClassification()).isEqualTo(RiskClassification.LOW);
        assertThat(analysis.confidenceLevel()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(analysis.indicators()).isEmpty();
        assertThat(analysis.targetUrl()).isEqualTo("https://www.example.com/[path-redacted]");
        assertThat(analysis.limitations()).anyMatch(limitation -> limitation.contains("not a safety guarantee"));
        assertThat(analysis.explanation().get("confirmedMaliciousIndicatorCount")).isEqualTo(0);
    }

    @Test
    void directIpHttpAndRedirectSignalsProduceAnExplainableHighRiskResult() {
        UrlAnalysis analysis = analyzer.analyze("http://192.0.2.1/login?redirect=https%3A%2F%2Fexample.test");

        assertThat(analysis.riskScore()).isGreaterThanOrEqualTo(50);
        assertThat(analysis.riskClassification()).isEqualTo(RiskClassification.HIGH);
        assertThat(analysis.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(codes(analysis)).contains("URL_NOT_HTTPS", "IP_ADDRESS_HOST", "REDIRECT_PARAMETER");
        assertThat(analysis.targetUrl()).isEqualTo("http://192.0.2.1/[path-redacted]?[query-redacted]");
    }

    @Test
    void lookalikeHostAndAbusedTldAreSignalsButNotConfirmedFraud() {
        UrlAnalysis analysis = analyzer.analyze("https://paypa1-secure.example.zip/login");

        assertThat(analysis.riskScore()).isGreaterThanOrEqualTo(50);
        assertThat(analysis.riskClassification()).isEqualTo(RiskClassification.HIGH);
        assertThat(codes(analysis)).contains("BRAND_LOOKALIKE", "HIGH_ABUSE_TLD", "OBFUSCATED_DOMAIN_LABEL");
        assertThat(analysis.indicators()).allMatch(indicator -> indicator.type().name().equals("SUSPICIOUS"));
        assertThat(analysis.limitations()).anyMatch(limitation -> limitation.contains("No indicator is confirmed malicious"));
    }

    @Test
    void officialBrandDomainIsNotMistakenForAnImpersonation() {
        UrlAnalysis analysis = analyzer.analyze("https://www.paypal.com/login");

        assertThat(codes(analysis)).doesNotContain("BRAND_LOOKALIKE");
    }

    @Test
    void onlyAbsoluteHttpAndHttpsUrlsAreAccepted() {
        assertThatThrownBy(() -> analyzer.analyze("ftp://example.com/file"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode()).isEqualTo("INVALID_URL"));
        assertThatThrownBy(() -> analyzer.analyze("not a url"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode()).isEqualTo("INVALID_URL"));
    }

    private Set<String> codes(UrlAnalysis analysis) {
        return analysis.indicators().stream().map(RiskSignal::code).collect(java.util.stream.Collectors.toSet());
    }
}
