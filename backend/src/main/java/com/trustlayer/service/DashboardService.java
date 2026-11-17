package com.trustlayer.service;

import com.trustlayer.dto.dashboard.DashboardActivityResponse;
import com.trustlayer.dto.dashboard.DashboardActivityType;
import com.trustlayer.dto.dashboard.DashboardRiskDistributionResponse;
import com.trustlayer.dto.dashboard.DashboardStatisticsResponse;
import com.trustlayer.entity.Scan;
import com.trustlayer.entity.ScanResult;
import com.trustlayer.entity.SimulatedTransaction;
import com.trustlayer.entity.ThreatReport;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.ScanType;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.ScanRepository;
import com.trustlayer.repository.ScanResultRepository;
import com.trustlayer.repository.SimulatedTransactionRepository;
import com.trustlayer.repository.ThreatReportRepository;
import com.trustlayer.repository.ThreatRepository;
import com.trustlayer.repository.UserAccountRepository;
import com.trustlayer.repository.projection.RiskClassificationCount;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owner-scoped dashboard read model built only from persisted application data. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<RiskClassification> REVIEW_RECOMMENDED = List.of(
            RiskClassification.MEDIUM, RiskClassification.HIGH, RiskClassification.CRITICAL
    );
    private static final int ACTIVITY_PER_SOURCE = 6;
    private static final int MAX_ACTIVITY = 12;

    private final UserAccountRepository userAccountRepository;
    private final ScanRepository scanRepository;
    private final ScanResultRepository scanResultRepository;
    private final SimulatedTransactionRepository transactionRepository;
    private final ThreatRepository threatRepository;
    private final ThreatReportRepository threatReportRepository;

    public DashboardService(
            UserAccountRepository userAccountRepository,
            ScanRepository scanRepository,
            ScanResultRepository scanResultRepository,
            SimulatedTransactionRepository transactionRepository,
            ThreatRepository threatRepository,
            ThreatReportRepository threatReportRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.scanRepository = scanRepository;
        this.scanResultRepository = scanResultRepository;
        this.transactionRepository = transactionRepository;
        this.threatRepository = threatRepository;
        this.threatReportRepository = threatReportRepository;
    }

    public DashboardStatisticsResponse statistics(Long userId) {
        findActiveUser(userId);
        List<DashboardActivityResponse> activity = recentActivity(userId);
        return new DashboardStatisticsResponse(
                scanRepository.countByUser_IdAndScanType(userId, ScanType.URL),
                scanResultRepository.countReviewRecommendedByUserIdAndScanType(userId, ScanType.URL, REVIEW_RECOMMENDED),
                scanRepository.countByUser_IdAndScanType(userId, ScanType.QR),
                scanResultRepository.countReviewRecommendedByUserIdAndScanType(userId, ScanType.QR, REVIEW_RECOMMENDED),
                transactionRepository.countByUser_Id(userId),
                transactionRepository.countByUser_IdAndRiskClassificationIn(userId, REVIEW_RECOMMENDED),
                threatRepository.count(),
                threatRepository.totalReportCount(),
                riskDistribution(userId),
                activity,
                "Scan and simulation metrics are scoped to your account. Community metrics are platform-wide counts of canonical records and accepted report submissions.",
                "Risk classifications are explainable review signals, not proof of safety, fraud, or maliciousness. Transaction metrics describe simulations only."
        );
    }

    private List<DashboardRiskDistributionResponse> riskDistribution(Long userId) {
        Map<RiskClassification, Long> counts = new EnumMap<>(RiskClassification.class);
        for (RiskClassification classification : RiskClassification.values()) {
            counts.put(classification, 0L);
        }
        addCounts(counts, scanResultRepository.countRiskDistributionByUserId(userId));
        addCounts(counts, transactionRepository.countRiskDistributionByUserId(userId));
        return counts.entrySet().stream()
                .map(entry -> new DashboardRiskDistributionResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void addCounts(Map<RiskClassification, Long> target, Collection<RiskClassificationCount> source) {
        for (RiskClassificationCount entry : source) {
            if (entry.getRiskClassification() != null) {
                target.merge(entry.getRiskClassification(), entry.getTotal(), Long::sum);
            }
        }
    }

    private List<DashboardActivityResponse> recentActivity(Long userId) {
        List<Scan> urlScans = scanRepository.findByUser_IdAndScanTypeOrderByRequestedAtDescIdDesc(
                userId, ScanType.URL, PageRequest.of(0, ACTIVITY_PER_SOURCE)
        ).getContent();
        List<Scan> qrScans = scanRepository.findByUser_IdAndScanTypeOrderByRequestedAtDescIdDesc(
                userId, ScanType.QR, PageRequest.of(0, ACTIVITY_PER_SOURCE)
        ).getContent();
        List<Scan> allScans = new ArrayList<>(urlScans.size() + qrScans.size());
        allScans.addAll(urlScans);
        allScans.addAll(qrScans);
        Map<Long, ScanResult> scanResults = scanResultsByScanId(allScans);

        List<DashboardActivityResponse> activity = new ArrayList<>();
        urlScans.forEach(scan -> activity.add(scanActivity(scan, scanResults.get(scan.getId()), DashboardActivityType.URL_SCAN)));
        qrScans.forEach(scan -> activity.add(scanActivity(scan, scanResults.get(scan.getId()), DashboardActivityType.QR_SCAN)));
        transactionRepository.findTop6ByUser_IdOrderByCreatedAtDescIdDesc(userId)
                .forEach(transaction -> activity.add(transactionActivity(transaction)));
        threatReportRepository.findTop6ByReporter_IdOrderByCreatedAtDescIdDesc(userId)
                .forEach(report -> activity.add(reportActivity(report)));

        return activity.stream()
                .sorted(Comparator.comparing(DashboardActivityResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_ACTIVITY)
                .toList();
    }

    private Map<Long, ScanResult> scanResultsByScanId(List<Scan> scans) {
        if (scans.isEmpty()) {
            return Map.of();
        }
        return scanResultRepository.findByScan_IdIn(scans.stream().map(Scan::getId).toList()).stream()
                .collect(Collectors.toMap(ScanResult::getScanId, Function.identity()));
    }

    private DashboardActivityResponse scanActivity(Scan scan, ScanResult result, DashboardActivityType type) {
        String title = type == DashboardActivityType.URL_SCAN ? "URL assessment" : "QR assessment";
        return new DashboardActivityResponse(
                scan.getId(),
                type,
                title,
                scan.getTargetValue(),
                scan.getCompletedAt() == null ? scan.getRequestedAt() : scan.getCompletedAt(),
                result == null ? null : result.getRiskClassification(),
                result == null ? null : result.getRiskScore()
        );
    }

    private DashboardActivityResponse transactionActivity(SimulatedTransaction transaction) {
        return new DashboardActivityResponse(
                transaction.getId(),
                DashboardActivityType.TRANSACTION_SIMULATION,
                "Transaction simulation",
                transaction.getBeneficiaryIdentifier(),
                transaction.getCreatedAt(),
                transaction.getRiskClassification(),
                transaction.getRiskScore()
        );
    }

    private DashboardActivityResponse reportActivity(ThreatReport report) {
        return new DashboardActivityResponse(
                report.getId(),
                DashboardActivityType.COMMUNITY_REPORT,
                "Community report submitted",
                report.getIndicatorValue(),
                report.getCreatedAt(),
                null,
                null
        );
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
