package com.trustlayer.dto.extension;

import java.time.Instant;

public record ExtensionTokenResponse(
        String accessToken,
        String tokenType,
        String scope,
        Instant expiresAt
) {
}
