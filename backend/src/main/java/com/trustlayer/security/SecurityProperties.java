package com.trustlayer.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
@Validated
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cookies cookies,
        @Valid @NotNull Privacy privacy,
        @Valid @NotNull Development development
) {

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String secret,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl
    ) {
    }

    public record Cookies(
            boolean secure,
            @NotBlank @Pattern(regexp = "(?i)Strict|Lax|None") String sameSite
    ) {
    }

    /**
     * A key used only for privacy-preserving deterministic matching of user-supplied
     * indicators. Production must set an independent high-entropy value; local development
     * may fall back to the JWT secret so a newly added setting does not strand an existing
     * developer environment.
     */
    public record Privacy(
            @NotBlank String indicatorHashSecret
    ) {
    }

    public record Development(
            boolean exposeActionTokens
    ) {
    }
}
