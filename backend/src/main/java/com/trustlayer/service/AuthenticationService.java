package com.trustlayer.service;

import com.trustlayer.dto.auth.ActionTokenResponse;
import com.trustlayer.dto.auth.AuthSessionResponse;
import com.trustlayer.dto.auth.EmailRequest;
import com.trustlayer.dto.auth.LoginRequest;
import com.trustlayer.dto.auth.RegisterRequest;
import com.trustlayer.dto.auth.RegistrationResponse;
import com.trustlayer.dto.auth.ResetPasswordRequest;
import com.trustlayer.dto.auth.TokenRequest;
import com.trustlayer.dto.user.UserResponse;
import com.trustlayer.entity.AuthenticationSession;
import com.trustlayer.entity.EmailVerificationToken;
import com.trustlayer.entity.PasswordResetToken;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.AuditOutcome;
import com.trustlayer.entity.enums.UserStatus;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.AuthenticationSessionRepository;
import com.trustlayer.repository.EmailVerificationTokenRepository;
import com.trustlayer.repository.PasswordResetTokenRepository;
import com.trustlayer.repository.UserAccountRepository;
import com.trustlayer.security.JwtTokenService;
import com.trustlayer.security.SecureTokenGenerator;
import com.trustlayer.security.SecurityProperties;
import com.trustlayer.security.TokenHashService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AuthenticationService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final UserAccountRepository userAccountRepository;
    private final AuthenticationSessionRepository sessionRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHashService tokenHashService;
    private final JwtTokenService jwtTokenService;
    private final SecurityProperties securityProperties;
    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;
    private final String dummyPasswordHash;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            AuthenticationSessionRepository sessionRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SecureTokenGenerator secureTokenGenerator,
            TokenHashService tokenHashService,
            JwtTokenService jwtTokenService,
            SecurityProperties securityProperties,
            LoginAttemptService loginAttemptService,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.sessionRepository = sessionRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.secureTokenGenerator = secureTokenGenerator;
        this.tokenHashService = tokenHashService;
        this.jwtTokenService = jwtTokenService;
        this.securityProperties = securityProperties;
        this.loginAttemptService = loginAttemptService;
        this.auditLogService = auditLogService;
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-trustlayer-password");
    }

    public RegistrationResponse register(RegisterRequest request, RequestMetadata metadata) {
        requireActionTokenDelivery();
        String email = normalizeEmail(request.email());
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                    "An account already exists for this email address.");
        }

        passwordPolicy.validate(request.password());
        UserAccount user = new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                normalizeOptionalName(request.firstName()),
                normalizeOptionalName(request.lastName())
        );
        userAccountRepository.save(user);

        String rawToken = issueVerificationToken(user, Instant.now());
        auditLogService.record(user, "AUTH.REGISTERED", "USER", user.getId().toString(), AuditOutcome.SUCCESS, metadata);

        return new RegistrationResponse(
                UserResponse.from(user),
                exposedForDevelopment(rawToken)
        );
    }

    public void verifyEmail(TokenRequest request, RequestMetadata metadata) {
        Instant now = Instant.now();
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHashForUpdate(tokenHashService.sha256(request.token()))
                .orElseThrow(() -> invalidActionToken("Verification token is invalid or expired."));

        if (!token.isUsableAt(now)) {
            throw invalidActionToken("Verification token is invalid or expired.");
        }

        UserAccount user = token.getUser();
        user.activate(now);
        token.consume(now);
        auditLogService.record(user, "AUTH.EMAIL_VERIFIED", "USER", user.getId().toString(), AuditOutcome.SUCCESS, metadata);
    }

    public ActionTokenResponse resendVerification(EmailRequest request, RequestMetadata metadata) {
        requireActionTokenDelivery();
        String email = normalizeEmail(request.email());
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).orElse(null);
        String rawToken = null;

        if (user != null && user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            rawToken = issueVerificationToken(user, Instant.now());
            auditLogService.record(user, "AUTH.EMAIL_VERIFICATION_REISSUED", "USER",
                    user.getId().toString(), AuditOutcome.SUCCESS, metadata);
        }

        return new ActionTokenResponse(exposedForDevelopment(rawToken));
    }

    public AuthResult login(LoginRequest request, RequestMetadata metadata) {
        Instant now = Instant.now();
        String email = normalizeEmail(request.email());
        String attemptKey = email + "|" + ipKey(metadata);
        loginAttemptService.assertAllowed(attemptKey, now);

        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            loginAttemptService.recordFailure(attemptKey, now);
            throw invalidCredentials();
        }

        if (passwordPolicy.exceedsBcryptInputLimit(request.password())) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            loginAttemptService.recordFailure(attemptKey, now);
            auditLogService.record(user, "AUTH.LOGIN_FAILED", "USER", user.getId().toString(), AuditOutcome.FAILURE, metadata);
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(attemptKey, now);
            auditLogService.record(user, "AUTH.LOGIN_FAILED", "USER", user.getId().toString(), AuditOutcome.FAILURE, metadata);
            throw invalidCredentials();
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            auditLogService.record(user, "AUTH.LOGIN_DENIED_EMAIL_UNVERIFIED", "USER",
                    user.getId().toString(), AuditOutcome.DENIED, metadata);
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Verify your email address before signing in.");
        }
        if (!user.isActive()) {
            auditLogService.record(user, "AUTH.LOGIN_DENIED_ACCOUNT_UNAVAILABLE", "USER",
                    user.getId().toString(), AuditOutcome.DENIED, metadata);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE",
                    "This account is unavailable.");
        }

        loginAttemptService.clear(attemptKey);
        user.recordSuccessfulLogin(now);
        AuthResult result = establishSession(user, metadata, now);
        auditLogService.record(user, "AUTH.LOGIN_SUCCEEDED", "USER", user.getId().toString(), AuditOutcome.SUCCESS, metadata);
        return result;
    }

    public AuthResult refresh(String rawRefreshToken, RequestMetadata metadata) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISSING", "Refresh token is required.");
        }

        Instant now = Instant.now();
        JwtTokenService.JwtSubject subject = jwtTokenService.parseRefreshToken(rawRefreshToken);
        AuthenticationSession session = sessionRepository.findBySessionIdForUpdate(subject.tokenId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid or expired."));

        String presentedHash = tokenHashService.sha256(rawRefreshToken);
        if (!session.getUser().getId().equals(subject.userId()) || !presentedHash.equals(session.getSessionTokenHash())) {
            session.revoke(now);
            auditLogService.record(session.getUser(), "AUTH.REFRESH_TOKEN_REUSE_DETECTED", "SESSION",
                    session.getSessionId(), AuditOutcome.DENIED, metadata);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                    "Refresh token is invalid or expired.");
        }

        UserAccount user = session.getUser();
        if (!session.isActiveAt(now) || !user.isActive()) {
            session.revoke(now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Session is no longer active.");
        }

        JwtTokenService.IssuedToken refreshToken = jwtTokenService.issueRefreshToken(user, session.getSessionId(), now);
        session.rotate(tokenHashService.sha256(refreshToken.value()), refreshToken.expiresAt(), now);
        JwtTokenService.IssuedToken accessToken = jwtTokenService.issueAccessToken(user, session.getSessionId(), now);
        auditLogService.record(user, "AUTH.TOKEN_REFRESHED", "SESSION", session.getSessionId(), AuditOutcome.SUCCESS, metadata);

        return new AuthResult(
                new AuthSessionResponse(accessToken.value(), "Bearer", accessToken.expiresAt(), UserResponse.from(user)),
                refreshToken.value(),
                refreshToken.expiresAt()
        );
    }

    public void logout(String rawRefreshToken, RequestMetadata metadata) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        try {
            JwtTokenService.JwtSubject subject = jwtTokenService.parseRefreshToken(rawRefreshToken);
            sessionRepository.findBySessionId(subject.tokenId()).ifPresent(session -> {
                if (tokenHashService.sha256(rawRefreshToken).equals(session.getSessionTokenHash())) {
                    session.revoke(Instant.now());
                    auditLogService.record(session.getUser(), "AUTH.LOGOUT", "SESSION",
                            session.getSessionId(), AuditOutcome.SUCCESS, metadata);
                }
            });
        } catch (ApiException ignored) {
            // Logout is idempotent; an expired or already-cleared cookie is not an error.
        }
    }

    public void logoutAll(Long userId, RequestMetadata metadata) {
        Instant now = Instant.now();
        UserAccount user = findActiveUser(userId);
        sessionRepository.revokeAllActiveByUserId(userId, now);
        auditLogService.record(user, "AUTH.LOGOUT_ALL", "USER", userId.toString(), AuditOutcome.SUCCESS, metadata);
    }

    public ActionTokenResponse requestPasswordReset(EmailRequest request, RequestMetadata metadata) {
        requireActionTokenDelivery();
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        String rawToken = null;

        if (user != null && user.isActive()) {
            rawToken = issuePasswordResetToken(user, Instant.now());
            auditLogService.record(user, "AUTH.PASSWORD_RESET_REQUESTED", "USER",
                    user.getId().toString(), AuditOutcome.SUCCESS, metadata);
        }

        // Always return the same public response shape to avoid account enumeration.
        return new ActionTokenResponse(exposedForDevelopment(rawToken));
    }

    public void resetPassword(ResetPasswordRequest request, RequestMetadata metadata) {
        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashForUpdate(tokenHashService.sha256(request.token()))
                .orElseThrow(() -> invalidActionToken("Password reset token is invalid or expired."));

        if (!token.isUsableAt(now) || !token.getUser().isActive()) {
            throw invalidActionToken("Password reset token is invalid or expired.");
        }

        passwordPolicy.validate(request.newPassword());
        UserAccount user = token.getUser();
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        token.consume(now);
        sessionRepository.revokeAllActiveByUserId(user.getId(), now);
        auditLogService.record(user, "AUTH.PASSWORD_RESET_COMPLETED", "USER",
                user.getId().toString(), AuditOutcome.SUCCESS, metadata);
    }

    private AuthResult establishSession(UserAccount user, RequestMetadata metadata, Instant now) {
        String sessionId = UUID.randomUUID().toString();
        JwtTokenService.IssuedToken refreshToken = jwtTokenService.issueRefreshToken(user, sessionId, now);
        sessionRepository.save(new AuthenticationSession(
                user,
                sessionId,
                tokenHashService.sha256(refreshToken.value()),
                metadata.ipAddress(),
                metadata.userAgent(),
                refreshToken.expiresAt(),
                now
        ));

        JwtTokenService.IssuedToken accessToken = jwtTokenService.issueAccessToken(user, sessionId, now);
        return new AuthResult(
                new AuthSessionResponse(accessToken.value(), "Bearer", accessToken.expiresAt(), UserResponse.from(user)),
                refreshToken.value(),
                refreshToken.expiresAt()
        );
    }

    private String issueVerificationToken(UserAccount user, Instant now) {
        emailVerificationTokenRepository.invalidateOutstandingForUser(user.getId(), now);
        String rawToken = secureTokenGenerator.nextToken();
        emailVerificationTokenRepository.save(new EmailVerificationToken(
                user,
                tokenHashService.sha256(rawToken),
                now.plus(VERIFICATION_TOKEN_TTL)
        ));
        return rawToken;
    }

    private String issuePasswordResetToken(UserAccount user, Instant now) {
        passwordResetTokenRepository.invalidateOutstandingForUser(user.getId(), now);
        String rawToken = secureTokenGenerator.nextToken();
        passwordResetTokenRepository.save(new PasswordResetToken(
                user,
                tokenHashService.sha256(rawToken),
                now.plus(PASSWORD_RESET_TOKEN_TTL)
        ));
        return rawToken;
    }

    private UserAccount findActiveUser(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                        "Authentication is required."));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "This account is unavailable.");
        }
        return user;
    }

    private String exposedForDevelopment(String rawToken) {
        return securityProperties.development().exposeActionTokens() ? rawToken : null;
    }

    /**
     * There is intentionally no pretend email integration. In local development the one-time
     * token may be exposed for a test client. In any environment where that is disabled, these
     * workflows fail closed until a real mail delivery adapter is configured.
     */
    private void requireActionTokenDelivery() {
        if (!securityProperties.development().exposeActionTokens()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_UNAVAILABLE",
                    "Email delivery is not configured for this environment.");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String ipKey(RequestMetadata metadata) {
        if (metadata.ipAddress() == null) {
            return "unknown";
        }
        StringBuilder value = new StringBuilder();
        for (byte octet : metadata.ipAddress()) {
            value.append(String.format("%02x", octet));
        }
        return value.toString();
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is invalid.");
    }

    private ApiException invalidActionToken(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACTION_TOKEN", message);
    }

    public record AuthResult(
            AuthSessionResponse response,
            String rawRefreshToken,
            Instant refreshTokenExpiresAt
    ) {
    }
}
