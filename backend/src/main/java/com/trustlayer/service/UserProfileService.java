package com.trustlayer.service;

import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.user.DeleteAccountRequest;
import com.trustlayer.dto.user.LoginHistoryResponse;
import com.trustlayer.dto.user.SessionResponse;
import com.trustlayer.dto.user.UpdateProfileRequest;
import com.trustlayer.dto.user.UserResponse;
import com.trustlayer.entity.AuditLog;
import com.trustlayer.entity.AuthenticationSession;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.AuditOutcome;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.AuthenticationSessionRepository;
import com.trustlayer.repository.AuditLogRepository;
import com.trustlayer.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class UserProfileService {

    private final UserAccountRepository userAccountRepository;
    private final AuthenticationSessionRepository sessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserProfileService(
            UserAccountRepository userAccountRepository,
            AuthenticationSessionRepository sessionRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.sessionRepository = sessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findActiveUser(userId));
    }

    public UserResponse updateProfile(Long userId, UpdateProfileRequest request, RequestMetadata metadata) {
        UserAccount user = findActiveUser(userId);
        user.updateProfile(normalizeName(request.firstName()), normalizeName(request.lastName()));
        auditLogService.record(user, "USER.PROFILE_UPDATED", "USER", userId.toString(), AuditOutcome.SUCCESS, metadata);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> getActiveSessions(
            Long userId,
            String currentSessionId,
            Pageable pageable
    ) {
        findActiveUser(userId);
        Page<SessionResponse> sessions = sessionRepository
                .findByUser_IdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now(), pageable)
                .map(session -> SessionResponse.from(session, currentSessionId));
        return PageResponse.from(sessions);
    }

    public void revokeSession(Long userId, String sessionId, RequestMetadata metadata) {
        AuthenticationSession session = sessionRepository.findBySessionIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session was not found."));

        Instant now = Instant.now();
        if (!session.isActiveAt(now)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session was not found.");
        }
        session.revoke(now);
        UserAccount user = findActiveUser(userId);
        auditLogService.record(user, "AUTH.SESSION_REVOKED", "SESSION", session.getSessionId(), AuditOutcome.SUCCESS, metadata);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginHistoryResponse> getLoginHistory(Long userId, Pageable pageable) {
        findActiveUser(userId);
        Page<LoginHistoryResponse> history = auditLogRepository.findByActor_IdAndEventTypeInOrderByCreatedAtDesc(
                        userId,
                        List.of(
                                "AUTH.LOGIN_SUCCEEDED",
                                "AUTH.LOGIN_FAILED",
                                "AUTH.LOGIN_DENIED_EMAIL_UNVERIFIED",
                                "AUTH.LOGIN_DENIED_ACCOUNT_UNAVAILABLE"
                        ),
                        pageable
                )
                .map(LoginHistoryResponse::from);
        return PageResponse.from(history);
    }

    public void deleteAccount(Long userId, DeleteAccountRequest request, RequestMetadata metadata) {
        UserAccount user = findActiveUser(userId);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Password is invalid.");
        }

        Instant now = Instant.now();
        sessionRepository.revokeAllActiveByUserId(userId, now);
        user.softDelete(now);
        auditLogService.record(user, "USER.ACCOUNT_SOFT_DELETED", "USER", userId.toString(), AuditOutcome.SUCCESS, metadata);
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

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
