package com.trustlayer.security;

import com.trustlayer.entity.enums.UserRole;

public record AuthenticatedUser(
        Long id,
        String email,
        UserRole role,
        String sessionId,
        JwtTokenService.TokenType tokenType
) {

    public boolean isBrowserAccessToken() {
        return tokenType == JwtTokenService.TokenType.BROWSER_ACCESS;
    }
}
