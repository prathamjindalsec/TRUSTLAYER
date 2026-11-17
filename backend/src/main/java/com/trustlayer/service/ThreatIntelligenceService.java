package com.trustlayer.service;

import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.threat.ThreatReportRequest;
import com.trustlayer.dto.threat.ThreatReportResponse;
import com.trustlayer.dto.threat.ThreatResponse;
import com.trustlayer.dto.threat.ThreatStatisticsResponse;
import com.trustlayer.dto.threat.ThreatVerificationRequest;
import com.trustlayer.entity.Threat;
import com.trustlayer.entity.ThreatReport;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.AuditOutcome;
import com.trustlayer.entity.enums.ThreatCategory;
import com.trustlayer.entity.enums.ThreatType;
import com.trustlayer.entity.enums.ThreatVerificationStatus;
import com.trustlayer.entity.enums.UserRole;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.ThreatReportRepository;
import com.trustlayer.repository.ThreatRepository;
import com.trustlayer.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Community threat workflow. A submitted report remains unverified unless an authenticated
 * ADMIN explicitly changes its canonical threat status through a separate review process.
 */
@Service
@Transactional
public class ThreatIntelligenceService {

    private final UserAccountRepository userAccountRepository;
    private final ThreatRepository threatRepository;
    private final ThreatReportRepository threatReportRepository;
    private final ThreatIndicatorCanonicalizer canonicalizer;
    private final ThreatReportRateLimiter rateLimiter;
    private final AuditLogService auditLogService;

    public ThreatIntelligenceService(
            UserAccountRepository userAccountRepository,
            ThreatRepository threatRepository,
            ThreatReportRepository threatReportRepository,
            ThreatIndicatorCanonicalizer canonicalizer,
            ThreatReportRateLimiter rateLimiter,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.threatRepository = threatRepository;
        this.threatReportRepository = threatReportRepository;
        this.canonicalizer = canonicalizer;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
    }

    public ThreatReportResponse report(Long userId, ThreatReportRequest request, RequestMetadata metadata) {
        UserAccount reporter = findActiveUser(userId);
        rateLimiter.assertReportAllowed(userId, Instant.now());
        ThreatIndicatorCanonicalizer.CanonicalThreatIndicator indicator = canonicalizer.canonicalize(
                request.indicatorType(), request.indicatorValue()
        );
        if (threatReportRepository.existsByReporter_IdAndIndicatorTypeAndIndicatorHash(
                userId, request.indicatorType(), indicator.indicatorHash())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REPORT",
                    "You have already reported this indicator. A second report would not add independent evidence.");
        }

        Instant now = Instant.now();
        Threat threat = threatRepository.findForReportUpdate(request.indicatorType(), indicator.indicatorHash()).orElse(null);
        boolean createdNewThreat = threat == null;
        if (createdNewThreat) {
            threat = new Threat(request.indicatorType(), indicator.safeDisplayValue(), indicator.indicatorHash(), request.category(), now);
            threat = threatRepository.save(threat);
        }
        boolean corroboratesExistingThreat = threat.getReportCount() > 0;
        threat.recordReport(now);

        ThreatReport report = threatReportRepository.save(new ThreatReport(
                reporter,
                threat,
                request.indicatorType(),
                indicator.safeDisplayValue(),
                indicator.indicatorHash(),
                request.category(),
                canonicalizer.sanitizeDescription(request.description(), request.indicatorType(), request.indicatorValue())
        ));
        auditLogService.record(reporter, "THREAT.REPORTED", "THREAT", threat.getId().toString(), AuditOutcome.SUCCESS, metadata);
        return new ThreatReportResponse(
                report.getId(), report.getReportStatus(), createdNewThreat, corroboratesExistingThreat,
                report.getCreatedAt(), ThreatResponse.from(threat)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ThreatResponse> search(
            Long userId,
            ThreatType type,
            ThreatCategory category,
            ThreatVerificationStatus verificationStatus,
            String query,
            Pageable pageable
    ) {
        findActiveUser(userId);
        if (type != null && query != null && !query.isBlank()) {
            ThreatIndicatorCanonicalizer.CanonicalThreatIndicator indicator = canonicalizer.canonicalize(type, query);
            Page<Threat> matched = threatRepository.findByThreatTypeAndIndicatorHash(type, indicator.indicatorHash())
                    .filter(threat -> matchesFilters(threat, category, verificationStatus))
                    .<Page<Threat>>map(threat -> new PageImpl<>(List.of(threat), pageable, 1))
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0));
            return PageResponse.from(matched.map(ThreatResponse::from));
        }
        String safeQuery = safeDisplayQuery(query);
        return PageResponse.from(threatRepository.search(type, category, verificationStatus, safeQuery, pageable)
                .map(ThreatResponse::from));
    }

    @Transactional(readOnly = true)
    public ThreatResponse findById(Long userId, Long threatId) {
        findActiveUser(userId);
        Threat threat = threatRepository.findById(threatId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "THREAT_NOT_FOUND", "Threat was not found."));
        return ThreatResponse.from(threat);
    }

    @Transactional(readOnly = true)
    public ThreatStatisticsResponse statistics(Long userId) {
        findActiveUser(userId);
        Map<ThreatCategory, Long> countsByCategory = new EnumMap<>(ThreatCategory.class);
        for (ThreatCategory category : ThreatCategory.values()) {
            countsByCategory.put(category, threatRepository.countByCategory(category));
        }
        List<ThreatResponse> candidates = threatRepository
                .findTop20ByReportCountGreaterThanEqualOrderByReportCountDescLastReportedAtDesc(3)
                .stream()
                .map(ThreatResponse::from)
                .toList();
        return new ThreatStatisticsResponse(
                threatRepository.count(),
                threatRepository.totalReportCount(),
                threatRepository.countByVerificationStatus(ThreatVerificationStatus.VERIFIED),
                threatRepository.countByVerificationStatus(ThreatVerificationStatus.UNVERIFIED),
                threatRepository.countByVerificationStatus(ThreatVerificationStatus.REJECTED),
                Map.copyOf(countsByCategory),
                candidates,
                "Repeated-report candidates are a frequency heuristic, not confirmed scam campaigns or proof of fraud."
        );
    }

    @Transactional(readOnly = true)
    public List<ThreatResponse> repeatedCampaignCandidates(Long userId, int minimumReports) {
        findActiveUser(userId);
        return threatRepository.findTop20ByReportCountGreaterThanEqualOrderByReportCountDescLastReportedAtDesc(minimumReports)
                .stream()
                .map(ThreatResponse::from)
                .toList();
    }

    public ThreatResponse updateVerification(
            Long adminUserId,
            Long threatId,
            ThreatVerificationRequest request,
            RequestMetadata metadata
    ) {
        UserAccount admin = findActiveUser(adminUserId);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_REQUIRED", "Administrator access is required.");
        }
        Threat threat = threatRepository.findById(threatId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "THREAT_NOT_FOUND", "Threat was not found."));
        threat.updateVerification(request.verificationStatus(), admin, Instant.now());
        auditLogService.record(admin, "THREAT.VERIFICATION_UPDATED", "THREAT", threat.getId().toString(), AuditOutcome.SUCCESS, metadata);
        return ThreatResponse.from(threat);
    }

    private boolean matchesFilters(Threat threat, ThreatCategory category, ThreatVerificationStatus verificationStatus) {
        return (category == null || threat.getCategory() == category)
                && (verificationStatus == null || threat.getVerificationStatus() == verificationStatus);
    }

    private String safeDisplayQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100 || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_THREAT_SEARCH", "query is invalid.");
        }
        return normalized;
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
}
