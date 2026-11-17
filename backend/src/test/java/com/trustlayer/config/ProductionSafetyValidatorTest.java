package com.trustlayer.config;

import com.trustlayer.security.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ProductionSafetyValidatorTest {

    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String HMAC_SECRET = "production-specific-hmac-secret-that-is-not-the-jwt-secret";

    @Test
    void acceptsAProductionSafeConfiguration() {
        assertThatCode(() -> new ProductionSafetyValidator(properties(true, false, JWT_SECRET, HMAC_SECRET)).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeCookieOrActionTokenSettings() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new ProductionSafetyValidator(properties(false, false, JWT_SECRET, HMAC_SECRET)).validate())
                .withMessageContaining("Secure authentication cookies");

        assertThatIllegalStateException()
                .isThrownBy(() -> new ProductionSafetyValidator(properties(true, true, JWT_SECRET, HMAC_SECRET)).validate())
                .withMessageContaining("must not expose email action tokens");
    }

    @Test
    void rejectsSharedOrWeakSigningSecrets() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new ProductionSafetyValidator(properties(true, false, JWT_SECRET, JWT_SECRET)).validate())
                .withMessageContaining("independent");

        assertThatIllegalStateException()
                .isThrownBy(() -> new ProductionSafetyValidator(properties(true, false, "c2hvcnQ=", HMAC_SECRET)).validate())
                .withMessageContaining("at least 32 bytes");
    }

    private SecurityProperties properties(boolean secureCookies, boolean exposeActionTokens, String jwtSecret, String hmacSecret) {
        return new SecurityProperties(
                new SecurityProperties.Jwt("trustlayer", jwtSecret, Duration.ofMinutes(15), Duration.ofDays(7)),
                new SecurityProperties.Cookies(secureCookies, "Strict"),
                new SecurityProperties.Privacy(hmacSecret),
                new SecurityProperties.Development(exposeActionTokens)
        );
    }
}
