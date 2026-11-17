package com.trustlayer.config;

import com.trustlayer.security.SecurityProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Refuses a clearly unsafe production configuration at startup. This is not a replacement for a
 * secret manager or TLS termination, but it prevents accidentally deploying the local-development
 * cookie or action-token settings just because an environment variable was omitted.
 */
@Component
@Profile("prod")
public class ProductionSafetyValidator implements SmartInitializingSingleton {

    private final SecurityProperties securityProperties;

    public ProductionSafetyValidator(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    void validate() {
        if (!securityProperties.cookies().secure()) {
            throw new IllegalStateException("Production requires Secure authentication cookies.");
        }
        if (securityProperties.development().exposeActionTokens()) {
            throw new IllegalStateException("Production must not expose email action tokens in API responses.");
        }
        if (securityProperties.jwt().secret().equals(securityProperties.privacy().indicatorHashSecret())) {
            throw new IllegalStateException("Production requires a privacy HMAC secret independent from the JWT secret.");
        }
        try {
            byte[] key = Base64.getDecoder().decode(securityProperties.jwt().secret());
            if (key.length < 32) {
                throw new IllegalStateException("Production JWT secret must decode to at least 32 bytes.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Production JWT secret must be Base64-encoded.", exception);
        }
    }
}
