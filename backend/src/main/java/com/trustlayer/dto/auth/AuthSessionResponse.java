package com.trustlayer.dto.auth;

import com.trustlayer.dto.user.UserResponse;

import java.time.Instant;

public record AuthSessionResponse(
        String accessToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        UserResponse user
) {
}
