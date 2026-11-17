package com.trustlayer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "authentication_sessions")
public class AuthenticationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "session_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String sessionId;

    @Column(name = "session_token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String sessionTokenHash;

    @Column(name = "ip_address", columnDefinition = "varbinary(16)")
    private byte[] ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthenticationSession() {
    }

    public AuthenticationSession(
            UserAccount user,
            String sessionId,
            String sessionTokenHash,
            byte[] ipAddress,
            String userAgent,
            Instant expiresAt,
            Instant now
    ) {
        this.user = user;
        this.sessionId = sessionId;
        this.sessionTokenHash = sessionTokenHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.lastSeenAt = now;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSessionTokenHash() {
        return sessionTokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void rotate(String replacementTokenHash, Instant replacementExpiry, Instant now) {
        sessionTokenHash = replacementTokenHash;
        expiresAt = replacementExpiry;
        lastSeenAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
