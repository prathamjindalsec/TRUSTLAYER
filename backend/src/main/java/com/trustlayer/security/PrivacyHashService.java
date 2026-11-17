package com.trustlayer.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Produces deterministic, keyed hashes for submitted identifiers that must be grouped without
 * persisting their raw form. A plain SHA-256 digest is unsuitable for low-entropy values such
 * as phone numbers because an attacker can cheaply enumerate possible inputs offline.
 */
@Service
public class PrivacyHashService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN_SEPARATOR = "trustlayer:indicator-hash:v1\u0000"
            .getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public PrivacyHashService(SecurityProperties securityProperties) {
        key = new SecretKeySpec(
                securityProperties.privacy().indicatorHashSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    public String indicatorHash(String normalizedValue) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            mac.update(DOMAIN_SEPARATOR);
            return HexFormat.of().formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable.", exception);
        }
    }
}
