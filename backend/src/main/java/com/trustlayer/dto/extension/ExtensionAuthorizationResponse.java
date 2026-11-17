package com.trustlayer.dto.extension;

import java.time.Instant;

public record ExtensionAuthorizationResponse(
        String authorizationCode,
        String redirectUri,
        String state,
        String scope,
        Instant expiresAt
) {
}
