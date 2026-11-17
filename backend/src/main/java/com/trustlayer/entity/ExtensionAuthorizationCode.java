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

/**
 * A one-time, PKCE-bound authorization code. Raw codes never reach the database: only their
 * SHA-256 digest is stored. The issued extension access token is stateless but remains tied to
 * this user's normal database-backed browser session through its session identifier claim.
 */
@Entity
@Table(name = "extension_authorization_codes")
public class ExtensionAuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "session_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String sessionId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "redirect_uri", nullable = false, length = 1024)
    private String redirectUri;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String codeHash;

    @Column(name = "code_challenge", nullable = false, length = 128)
    private String codeChallenge;

    @Column(nullable = false, length = 100)
    private String scope;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExtensionAuthorizationCode() {
    }

    public ExtensionAuthorizationCode(
            UserAccount user,
            String sessionId,
            String clientId,
            String redirectUri,
            String codeHash,
            String codeChallenge,
            String scope,
            Instant expiresAt
    ) {
        this.user = user;
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.codeHash = codeHash;
        this.codeChallenge = codeChallenge;
        this.scope = scope;
        this.expiresAt = expiresAt;
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

    public String getClientId() {
        return clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }

    public String getScope() {
        return scope;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant now) {
        if (consumedAt == null) {
            consumedAt = now;
        }
    }
}
