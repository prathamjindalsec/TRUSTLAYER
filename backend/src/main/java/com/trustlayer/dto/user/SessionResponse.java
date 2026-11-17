package com.trustlayer.dto.user;

import com.trustlayer.entity.AuthenticationSession;

import java.time.Instant;

/** Public representation deliberately excludes the refresh token and its hash. */
public record SessionResponse(
        String sessionId,
        String userAgent,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current
) {

    public static SessionResponse from(AuthenticationSession session, String currentSessionId) {
        return new SessionResponse(
                session.getSessionId(),
                session.getUserAgent(),
                session.getCreatedAt(),
                session.getLastSeenAt(),
                session.getExpiresAt(),
                session.getSessionId().equals(currentSessionId)
        );
    }
}
