package com.trustlayer.security;

import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.UserRole;
import com.trustlayer.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenService {

    private static final String TOKEN_USE_CLAIM = "token_use";
    private static final String ROLE_CLAIM = "role";
    private static final String SESSION_ID_CLAIM = "sid";
    private static final String TOKEN_AUDIENCE_CLAIM = "token_audience";
    private static final String SCOPE_CLAIM = "scope";
    public static final String EXTENSION_AUDIENCE = "trustlayer-chrome-extension";
    public static final String EXTENSION_URL_SCAN_SCOPE = "url:scan";

    private final SecurityProperties securityProperties;
    private final SecretKey signingKey;

    public JwtTokenService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;

        byte[] secretBytes;
        try {
            secretBytes = Decoders.BASE64.decode(securityProperties.jwt().secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("TRUSTLAYER_JWT_SECRET must be Base64 encoded.", exception);
        }

        if (secretBytes.length < 32) {
            throw new IllegalStateException("TRUSTLAYER_JWT_SECRET must contain at least 256 bits.");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public IssuedToken issueAccessToken(UserAccount user, String sessionId, Instant now) {
        Instant expiresAt = now.plus(securityProperties.jwt().accessTokenTtl());
        String token = buildToken(
                user,
                UUID.randomUUID().toString(),
                sessionId,
                TokenType.BROWSER_ACCESS,
                now,
                expiresAt,
                Map.of()
        );
        return new IssuedToken(token, expiresAt);
    }

    public IssuedToken issueRefreshToken(UserAccount user, String sessionId, Instant now) {
        Instant expiresAt = now.plus(securityProperties.jwt().refreshTokenTtl());
        String token = buildToken(
                user,
                sessionId,
                sessionId,
                TokenType.REFRESH,
                now,
                expiresAt,
                Map.of()
        );
        return new IssuedToken(token, expiresAt);
    }

    /**
     * A deliberately short-lived bearer token for the installed extension. It has no refresh
     * capability, no browser-cookie dependency, one audience, and one scope. Its session ID is
     * still checked by JwtAuthenticationFilter, so normal logout or session revocation disables it.
     */
    public IssuedToken issueExtensionUrlScanToken(
            UserAccount user,
            String sessionId,
            Instant now,
            Duration ttl
    ) {
        Instant expiresAt = now.plus(ttl);
        String token = buildToken(
                user,
                UUID.randomUUID().toString(),
                sessionId,
                TokenType.EXTENSION_URL_SCAN,
                now,
                expiresAt,
                Map.of(
                        TOKEN_AUDIENCE_CLAIM, EXTENSION_AUDIENCE,
                        SCOPE_CLAIM, EXTENSION_URL_SCAN_SCOPE
                )
        );
        return new IssuedToken(token, expiresAt);
    }

    public JwtSubject parseAccessToken(String token) {
        return parseToken(token, TokenType.BROWSER_ACCESS);
    }

    public JwtSubject parseRefreshToken(String token) {
        return parseToken(token, TokenType.REFRESH);
    }

    /**
     * Parses a credential intended for an authenticated HTTP request. Refresh tokens are never
     * accepted at this layer. Extension credentials are recognized distinctly so the filter can
     * prevent them from being replayed against the broader browser API.
     */
    public JwtSubject parseAuthenticationToken(String token) {
        try {
            Claims claims = parseClaims(token);
            TokenType tokenType = TokenType.fromClaim(claims.get(TOKEN_USE_CLAIM, String.class));
            if (tokenType == TokenType.REFRESH) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN_TYPE", "Token cannot be used for this operation.");
            }
            validateAdditionalClaims(claims, tokenType);
            return toSubject(claims, tokenType);
        } catch (ApiException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private String buildToken(
            UserAccount user,
            String tokenId,
            String sessionId,
            TokenType tokenType,
            Instant issuedAt,
            Instant expiresAt,
            Map<String, String> additionalClaims
    ) {
        var builder = Jwts.builder()
                .issuer(securityProperties.jwt().issuer())
                .subject(user.getId().toString())
                .id(tokenId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_USE_CLAIM, tokenType.claimValue())
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim(SESSION_ID_CLAIM, sessionId);
        additionalClaims.forEach(builder::claim);
        return builder.signWith(signingKey).compact();
    }

    private JwtSubject parseToken(String token, TokenType expectedTokenType) {
        try {
            Claims claims = parseClaims(token);
            TokenType tokenType = TokenType.fromClaim(claims.get(TOKEN_USE_CLAIM, String.class));
            if (tokenType != expectedTokenType) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN_TYPE", "Token cannot be used for this operation.");
            }
            validateAdditionalClaims(claims, tokenType);
            return toSubject(claims, tokenType);
        } catch (ApiException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(securityProperties.jwt().issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void validateAdditionalClaims(Claims claims, TokenType tokenType) {
        if (tokenType != TokenType.EXTENSION_URL_SCAN) {
            return;
        }

        String audience = claims.get(TOKEN_AUDIENCE_CLAIM, String.class);
        String scope = claims.get(SCOPE_CLAIM, String.class);
        if (!EXTENSION_AUDIENCE.equals(audience) || !EXTENSION_URL_SCAN_SCOPE.equals(scope)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Authentication token is invalid or expired.");
        }
    }

    private JwtSubject toSubject(Claims claims, TokenType tokenType) {
        String subject = claims.getSubject();
        String roleValue = claims.get(ROLE_CLAIM, String.class);
        String tokenId = claims.getId();
        String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
        Date expiration = claims.getExpiration();
        if (subject == null || subject.isBlank()
                || roleValue == null || roleValue.isBlank()
                || tokenId == null || tokenId.isBlank()
                || sessionId == null || sessionId.isBlank()
                || expiration == null) {
            throw new IllegalArgumentException("Token is missing required claims.");
        }
        Long userId = Long.valueOf(subject);
        UserRole role = UserRole.valueOf(roleValue);
        Instant expiresAt = expiration.toInstant();
        return new JwtSubject(userId, tokenId, sessionId, role, expiresAt, tokenType);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Authentication token is invalid or expired.");
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record JwtSubject(
            Long userId,
            String tokenId,
            String sessionId,
            UserRole role,
            Instant expiresAt,
            TokenType tokenType
    ) {
    }

    public enum TokenType {
        BROWSER_ACCESS("access"),
        REFRESH("refresh"),
        EXTENSION_URL_SCAN("extension_url_scan");

        private final String claimValue;

        TokenType(String claimValue) {
            this.claimValue = claimValue;
        }

        public String claimValue() {
            return claimValue;
        }

        private static TokenType fromClaim(String value) {
            for (TokenType candidate : values()) {
                if (candidate.claimValue.equals(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("Token has an unsupported use.");
        }
    }
}
