package com.trustlayer.service;

import com.trustlayer.config.ExtensionProperties;
import com.trustlayer.dto.extension.ExtensionAuthorizationRequest;
import com.trustlayer.dto.extension.ExtensionAuthorizationResponse;
import com.trustlayer.dto.extension.ExtensionTokenRequest;
import com.trustlayer.dto.extension.ExtensionTokenResponse;
import com.trustlayer.entity.AuthenticationSession;
import com.trustlayer.entity.ExtensionAuthorizationCode;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.AuditOutcome;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.AuthenticationSessionRepository;
import com.trustlayer.repository.ExtensionAuthorizationCodeRepository;
import com.trustlayer.repository.UserAccountRepository;
import com.trustlayer.security.JwtTokenService;
import com.trustlayer.security.SecureTokenGenerator;
import com.trustlayer.security.TokenHashService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * OAuth-style authorization-code exchange for the installed extension. It uses PKCE because a
 * browser extension is a public client: no client secret can safely be embedded in its package.
 */
@Service
public class ExtensionAuthorizationService {

    private static final String URL_SCAN_SCOPE = JwtTokenService.EXTENSION_URL_SCAN_SCOPE;

    private final ExtensionProperties extensionProperties;
    private final ExtensionAuthorizationCodeRepository authorizationCodeRepository;
    private final AuthenticationSessionRepository sessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashService tokenHashService;
    private final JwtTokenService jwtTokenService;
    private final AuditLogService auditLogService;

    public ExtensionAuthorizationService(
            ExtensionProperties extensionProperties,
            ExtensionAuthorizationCodeRepository authorizationCodeRepository,
            AuthenticationSessionRepository sessionRepository,
            UserAccountRepository userAccountRepository,
            SecureTokenGenerator secureTokenGenerator,
            TokenHashService tokenHashService,
            JwtTokenService jwtTokenService,
            AuditLogService auditLogService
    ) {
        this.extensionProperties = extensionProperties;
        this.authorizationCodeRepository = authorizationCodeRepository;
        this.sessionRepository = sessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.tokenHashService = tokenHashService;
        this.jwtTokenService = jwtTokenService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ExtensionAuthorizationResponse authorize(
            Long userId,
            String sessionId,
            ExtensionAuthorizationRequest request,
            RequestMetadata metadata
    ) {
        assertExtensionIsConfigured();
        assertClient(request.clientId(), request.redirectUri());

        Instant now = Instant.now();
        UserAccount user = userAccountRepository.findById(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                        "Authentication is required."));
        boolean activeSession = sessionRepository.existsBySessionIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(
                sessionId,
                userId,
                now
        );
        if (!activeSession) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Session is no longer active.");
        }

        String rawCode = secureTokenGenerator.nextToken();
        Instant expiresAt = now.plus(extensionProperties.authorizationCodeTtl());
        ExtensionAuthorizationCode authorizationCode = authorizationCodeRepository.save(new ExtensionAuthorizationCode(
                user,
                sessionId,
                request.clientId(),
                request.redirectUri(),
                tokenHashService.sha256(rawCode),
                request.codeChallenge(),
                URL_SCAN_SCOPE,
                expiresAt
        ));
        auditLogService.record(user, "EXTENSION.AUTHORIZATION_GRANTED", "EXTENSION_AUTHORIZATION",
                authorizationCode.getId().toString(), AuditOutcome.SUCCESS, metadata);

        return new ExtensionAuthorizationResponse(
                rawCode,
                request.redirectUri(),
                request.state(),
                URL_SCAN_SCOPE,
                expiresAt
        );
    }

    @Transactional
    public ExtensionTokenResponse exchange(
            ExtensionTokenRequest request,
            RequestMetadata metadata
    ) {
        assertExtensionIsConfigured();
        assertClient(request.clientId(), request.redirectUri());

        Instant now = Instant.now();
        ExtensionAuthorizationCode authorizationCode = authorizationCodeRepository
                .findByCodeHashForUpdate(tokenHashService.sha256(request.code()))
                .orElseThrow(this::invalidAuthorizationCode);
        if (!authorizationCode.isUsableAt(now)
                || !authorizationCode.getClientId().equals(request.clientId())
                || !authorizationCode.getRedirectUri().equals(request.redirectUri())) {
            throw invalidAuthorizationCode();
        }

        String calculatedChallenge = s256Challenge(request.codeVerifier());
        if (!MessageDigest.isEqual(
                authorizationCode.getCodeChallenge().getBytes(StandardCharsets.US_ASCII),
                calculatedChallenge.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw invalidAuthorizationCode();
        }

        UserAccount user = authorizationCode.getUser();
        boolean activeSession = user.isActive() && sessionRepository
                .existsBySessionIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(
                        authorizationCode.getSessionId(),
                        user.getId(),
                        now
                );
        if (!activeSession) {
            throw invalidAuthorizationCode();
        }

        authorizationCode.consume(now);
        JwtTokenService.IssuedToken token = jwtTokenService.issueExtensionUrlScanToken(
                user,
                authorizationCode.getSessionId(),
                now,
                extensionProperties.accessTokenTtl()
        );
        auditLogService.record(user, "EXTENSION.TOKEN_EXCHANGED", "EXTENSION_AUTHORIZATION",
                authorizationCode.getId().toString(), AuditOutcome.SUCCESS, metadata);

        return new ExtensionTokenResponse(token.value(), "Bearer", authorizationCode.getScope(), token.expiresAt());
    }

    private void assertExtensionIsConfigured() {
        if (!extensionProperties.enabled() || !extensionProperties.hasRequiredConfiguration()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXTENSION_UNAVAILABLE",
                    "Browser extension authorization is not configured for this environment.");
        }

        URI redirectUri = parseUri(extensionProperties.redirectUri());
        if (!"https".equalsIgnoreCase(redirectUri.getScheme())
                || redirectUri.getHost() == null
                || !redirectUri.getHost().endsWith(".chromiumapp.org")
                || redirectUri.getRawQuery() != null
                || redirectUri.getRawFragment() != null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXTENSION_UNAVAILABLE",
                    "Browser extension authorization configuration is invalid.");
        }
        String extensionId = redirectUri.getHost().substring(0,
                redirectUri.getHost().length() - ".chromiumapp.org".length());
        if (extensionId.isBlank() || !extensionProperties.origin().equals("chrome-extension://" + extensionId)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXTENSION_UNAVAILABLE",
                    "Browser extension authorization configuration is invalid.");
        }
    }

    private void assertClient(String clientId, String redirectUri) {
        if (!extensionProperties.clientId().equals(clientId) || !extensionProperties.redirectUri().equals(redirectUri)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXTENSION_CLIENT_REJECTED",
                    "Extension authorization request was rejected.");
        }
    }

    private URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXTENSION_UNAVAILABLE",
                    "Browser extension authorization configuration is invalid.");
        }
    }

    private String s256Challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private ApiException invalidAuthorizationCode() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_EXTENSION_AUTHORIZATION",
                "Extension authorization is invalid or expired.");
    }
}
