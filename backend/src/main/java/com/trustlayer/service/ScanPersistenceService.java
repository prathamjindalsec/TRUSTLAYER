package com.trustlayer.service;

import com.trustlayer.entity.RiskIndicator;
import com.trustlayer.entity.Scan;
import com.trustlayer.entity.ScanResult;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.AuditOutcome;
import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.ScanType;
import com.trustlayer.repository.RiskIndicatorRepository;
import com.trustlayer.repository.ScanRepository;
import com.trustlayer.repository.ScanResultRepository;
import com.trustlayer.risk.RiskSignal;
import com.trustlayer.security.TokenHashService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Shared persistence path for URL and QR scans. It prevents drift in lifecycle transitions,
 * target hashing, risk-indicator storage, and audit events as additional scan types are added.
 */
@Service
@Transactional
public class ScanPersistenceService {

    private final ScanRepository scanRepository;
    private final ScanResultRepository scanResultRepository;
    private final RiskIndicatorRepository riskIndicatorRepository;
    private final TokenHashService tokenHashService;
    private final AuditLogService auditLogService;

    public ScanPersistenceService(
            ScanRepository scanRepository,
            ScanResultRepository scanResultRepository,
            RiskIndicatorRepository riskIndicatorRepository,
            TokenHashService tokenHashService,
            AuditLogService auditLogService
    ) {
        this.scanRepository = scanRepository;
        this.scanResultRepository = scanResultRepository;
        this.riskIndicatorRepository = riskIndicatorRepository;
        this.tokenHashService = tokenHashService;
        this.auditLogService = auditLogService;
    }

    public PersistedScan persist(
            UserAccount user,
            ScanType scanType,
            String privacyRedactedTarget,
            int riskScore,
            RiskClassification riskClassification,
            ConfidenceLevel confidenceLevel,
            List<String> limitations,
            Map<String, Object> explanation,
            String analyzerVersion,
            List<RiskSignal> signals,
            String auditEventType,
            RequestMetadata metadata
    ) {
        Instant now = Instant.now();
        Scan scan = new Scan(
                user,
                scanType,
                privacyRedactedTarget,
                tokenHashService.sha256(privacyRedactedTarget),
                now
        );
        scan.markProcessing();
        scan = scanRepository.save(scan);

        ScanResult result = new ScanResult(
                scan,
                riskScore,
                riskClassification,
                confidenceLevel,
                String.join(" ", limitations),
                explanation,
                analyzerVersion,
                now
        );
        result = scanResultRepository.save(result);

        ScanResult persistedResult = result;
        List<RiskIndicator> indicators = signals.stream()
                .map(signal -> new RiskIndicator(
                        persistedResult,
                        signal.code(),
                        signal.name(),
                        signal.type(),
                        signal.severity(),
                        signal.weight(),
                        signal.evidence()
                ))
                .toList();
        List<RiskIndicator> savedIndicators = riskIndicatorRepository.saveAll(indicators);

        scan.markCompleted(now);
        auditLogService.record(user, auditEventType, "SCAN", scan.getId().toString(), AuditOutcome.SUCCESS, metadata);
        return new PersistedScan(scan, result, savedIndicators);
    }

    public record PersistedScan(Scan scan, ScanResult result, List<RiskIndicator> indicators) {
        public PersistedScan {
            indicators = List.copyOf(indicators);
        }
    }
}
