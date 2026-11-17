package com.trustlayer.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Explicit allowlist settings for the browser extension authorization flow. The extension stays
 * disabled unless every value is configured; accepting arbitrary Chrome extension origins or
 * redirect URIs would let another extension attempt to obtain a user grant.
 */
@ConfigurationProperties(prefix = "app.extension")
@Validated
public record ExtensionProperties(
        boolean enabled,
        String clientId,
        String redirectUri,
        String origin,
        @NotNull Duration authorizationCodeTtl,
        @NotNull Duration accessTokenTtl
) {

    public boolean hasRequiredConfiguration() {
        return clientId != null && !clientId.isBlank()
                && redirectUri != null && !redirectUri.isBlank()
                && origin != null && !origin.isBlank();
    }
}
