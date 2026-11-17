package com.trustlayer.service;

import com.trustlayer.analyzer.qr.DecodedQrPayload;
import com.trustlayer.analyzer.qr.QrImageDecoder;
import com.trustlayer.analyzer.qr.QrPayloadAnalysis;
import com.trustlayer.analyzer.qr.QrPayloadAnalyzer;
import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.scanner.QrScanHistoryItemResponse;
import com.trustlayer.dto.scanner.QrScanResponse;
import com.trustlayer.entity.RiskIndicator;
import com.trustlayer.entity.Scan;
import com.trustlayer.entity.ScanResult;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.ScanType;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.RiskIndicatorRepository;
import com.trustlayer.repository.ScanRepository;
import com.trustlayer.repository.ScanResultRepository;
import com.trustlayer.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Authenticated, owner-scoped QR scanning and persistence. The raw upload and raw decoded text
 * remain in memory only for the duration of an accepted request.
 */
@Service
@Transactional
public class QrScannerService {

    private final QrImageDecoder qrImageDecoder;
    private final QrPayloadAnalyzer qrPayloadAnalyzer;
    private final UserAccountRepository userAccountRepository;
    private final ScanRepository scanRepository;
    private final ScanResultRepository scanResultRepository;
    private final RiskIndicatorRepository riskIndicatorRepository;
    private final ScanSubmissionRateLimiter rateLimiter;
    private final ScanPersistenceService scanPersistenceService;

    public QrScannerService(
            QrImageDecoder qrImageDecoder,
            QrPayloadAnalyzer qrPayloadAnalyzer,
            UserAccountRepository userAccountRepository,
            ScanRepository scanRepository,
            ScanResultRepository scanResultRepository,
            RiskIndicatorRepository riskIndicatorRepository,
            ScanSubmissionRateLimiter rateLimiter,
            ScanPersistenceService scanPersistenceService
    ) {
        this.qrImageDecoder = qrImageDecoder;
        this.qrPayloadAnalyzer = qrPayloadAnalyzer;
        this.userAccountRepository = userAccountRepository;
        this.scanRepository = scanRepository;
        this.scanResultRepository = scanResultRepository;
        this.riskIndicatorRepository = riskIndicatorRepository;
        this.rateLimiter = rateLimiter;
        this.scanPersistenceService = scanPersistenceService;
    }

    public QrScanResponse scan(Long userId, MultipartFile image, RequestMetadata metadata) {
        UserAccount user = findActiveUser(userId);
        rateLimiter.assertSubmissionAllowed(userId, ScanType.QR, Instant.now());
        DecodedQrPayload decoded = qrImageDecoder.decode(image);
        QrPayloadAnalysis analysis = qrPayloadAnalyzer.analyze(decoded);

        ScanPersistenceService.PersistedScan persisted = scanPersistenceService.persist(
                user,
                ScanType.QR,
                analysis.safeTarget(),
                analysis.riskScore(),
                analysis.riskClassification(),
                analysis.confidenceLevel(),
                analysis.limitations(),
                analysis.explanation(),
                analysis.analyzerVersion(),
                analysis.indicators(),
                "SCANNER.QR_ANALYZED",
                metadata
        );
        return QrScanResponse.from(persisted.scan(), persisted.result(), persisted.indicators());
    }

    @Transactional(readOnly = true)
    public PageResponse<QrScanHistoryItemResponse> history(Long userId, Pageable pageable) {
        findActiveUser(userId);
        Page<Scan> scans = scanRepository.findByUser_IdAndScanTypeOrderByRequestedAtDescIdDesc(userId, ScanType.QR, pageable);
        Map<Long, ScanResult> resultsByScanId = scanResultsByScanIds(scans.getContent().stream().map(Scan::getId).toList());
        List<QrScanHistoryItemResponse> items = scans.getContent().stream()
                .map(scan -> QrScanHistoryItemResponse.from(scan, resultsByScanId.get(scan.getId())))
                .toList();
        return new PageResponse<>(items, scans.getNumber(), scans.getSize(), scans.getTotalElements(), scans.getTotalPages(), scans.hasNext());
    }

    @Transactional(readOnly = true)
    public QrScanResponse findById(Long userId, Long scanId) {
        findActiveUser(userId);
        Scan scan = scanRepository.findByIdAndUser_IdAndScanType(scanId, userId, ScanType.QR)
                // Do not disclose whether another user owns this scan ID.
                .orElseThrow(this::scanNotFound);
        ScanResult result = scanResultRepository.findByScan_Id(scan.getId()).orElseThrow(this::scanNotFound);
        List<RiskIndicator> indicators = riskIndicatorRepository.findByScanResult_IdOrderByIdAsc(result.getId());
        return QrScanResponse.from(scan, result, indicators);
    }

    private Map<Long, ScanResult> scanResultsByScanIds(Collection<Long> scanIds) {
        if (scanIds.isEmpty()) {
            return Map.of();
        }
        return scanResultRepository.findByScan_IdIn(scanIds).stream()
                .collect(Collectors.toMap(
                        ScanResult::getScanId,
                        result -> result,
                        (first, ignored) -> first,
                        HashMap::new
                ));
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

    private ApiException scanNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND", "Scan was not found.");
    }
}
