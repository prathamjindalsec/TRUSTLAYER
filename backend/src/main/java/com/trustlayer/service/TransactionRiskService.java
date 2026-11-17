package com.trustlayer.service;

import com.trustlayer.analyzer.transaction.HistoricalTransaction;
import com.trustlayer.analyzer.transaction.TransactionRiskAnalysis;
import com.trustlayer.analyzer.transaction.TransactionRiskAnalyzer;
import com.trustlayer.analyzer.transaction.TransactionRiskInput;
import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.transaction.TransactionAnalyzeRequest;
import com.trustlayer.dto.transaction.TransactionRiskHistoryItemResponse;
import com.trustlayer.dto.transaction.TransactionRiskResponse;
import com.trustlayer.entity.RiskIndicator;
import com.trustlayer.entity.SimulatedTransaction;
import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.AuditOutcome;
import com.trustlayer.entity.enums.TransactionAnalysisStatus;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.RiskIndicatorRepository;
import com.trustlayer.repository.SimulatedTransactionRepository;
import com.trustlayer.repository.UserAccountRepository;
import com.trustlayer.security.TokenHashService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Persists only simulated transaction analyses. This service cannot send money, contact a bank,
 * or alter a payment instruction; it turns user-entered demo values into an explainable result.
 */
@Service
@Transactional
public class TransactionRiskService {

    private static final String REDACTED_BENEFICIARY_DISPLAY = "Beneficiary identifier (redacted)";

    private final TransactionRiskAnalyzer transactionRiskAnalyzer;
    private final UserAccountRepository userAccountRepository;
    private final SimulatedTransactionRepository transactionRepository;
    private final RiskIndicatorRepository riskIndicatorRepository;
    private final TokenHashService tokenHashService;
    private final TransactionAnalysisRateLimiter rateLimiter;
    private final AuditLogService auditLogService;

    public TransactionRiskService(
            TransactionRiskAnalyzer transactionRiskAnalyzer,
            UserAccountRepository userAccountRepository,
            SimulatedTransactionRepository transactionRepository,
            RiskIndicatorRepository riskIndicatorRepository,
            TokenHashService tokenHashService,
            TransactionAnalysisRateLimiter rateLimiter,
            AuditLogService auditLogService
    ) {
        this.transactionRiskAnalyzer = transactionRiskAnalyzer;
        this.userAccountRepository = userAccountRepository;
        this.transactionRepository = transactionRepository;
        this.riskIndicatorRepository = riskIndicatorRepository;
        this.tokenHashService = tokenHashService;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
    }

    public TransactionRiskResponse analyze(Long userId, TransactionAnalyzeRequest request, RequestMetadata metadata) {
        UserAccount user = findActiveUser(userId);
        rateLimiter.assertSubmissionAllowed(userId, Instant.now());

        String normalizedBeneficiary = normalizeBeneficiary(request.beneficiaryIdentifier());
        String beneficiaryHash = tokenHashService.sha256(normalizedBeneficiary);
        List<HistoricalTransaction> history = transactionRepository
                .findTop50ByUser_IdAndBeneficiaryHashAndAnalysisStatusOrderByTransactionTimeDescIdDesc(
                        userId, beneficiaryHash, TransactionAnalysisStatus.COMPLETED
                )
                .stream()
                .map(transaction -> new HistoricalTransaction(
                        transaction.getAmount(), transaction.getTransactionTime(), transaction.getRiskScore()
                ))
                .toList();

        String normalizedCurrency = request.currency().trim().toUpperCase(Locale.ROOT);
        TransactionRiskAnalysis analysis = transactionRiskAnalyzer.analyze(new TransactionRiskInput(
                request.amount(), normalizedCurrency, request.transactionTime(), history
        ));

        SimulatedTransaction transaction = transactionRepository.save(new SimulatedTransaction(
                user,
                UUID.randomUUID().toString(),
                request.amount(),
                normalizedCurrency,
                REDACTED_BENEFICIARY_DISPLAY,
                beneficiaryHash,
                request.transactionTime().toInstant()
        ));
        transaction.markCompleted(
                analysis.riskScore(),
                analysis.riskClassification(),
                analysis.confidenceLevel(),
                analysis.explanation()
        );
        List<RiskIndicator> indicators = analysis.indicators().stream()
                .map(signal -> new RiskIndicator(
                        transaction,
                        signal.code(),
                        signal.name(),
                        signal.type(),
                        signal.severity(),
                        signal.weight(),
                        signal.evidence()
                ))
                .toList();
        List<RiskIndicator> savedIndicators = riskIndicatorRepository.saveAll(indicators);
        auditLogService.record(user, "TRANSACTION.SIMULATION_ANALYZED", "TRANSACTION", transaction.getId().toString(),
                AuditOutcome.SUCCESS, metadata);
        return TransactionRiskResponse.from(transaction, savedIndicators);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionRiskHistoryItemResponse> history(Long userId, Pageable pageable) {
        findActiveUser(userId);
        Page<SimulatedTransaction> transactions = transactionRepository.findByUser_IdOrderByTransactionTimeDescIdDesc(userId, pageable);
        List<TransactionRiskHistoryItemResponse> items = transactions.getContent().stream()
                .map(TransactionRiskHistoryItemResponse::from)
                .toList();
        return new PageResponse<>(items, transactions.getNumber(), transactions.getSize(),
                transactions.getTotalElements(), transactions.getTotalPages(), transactions.hasNext());
    }

    @Transactional(readOnly = true)
    public TransactionRiskResponse findById(Long userId, Long transactionId) {
        findActiveUser(userId);
        SimulatedTransaction transaction = transactionRepository.findByIdAndUser_Id(transactionId, userId)
                // Do not reveal the existence of another user's transaction simulation.
                .orElseThrow(this::transactionNotFound);
        List<RiskIndicator> indicators = riskIndicatorRepository.findByTransaction_IdOrderByIdAsc(transaction.getId());
        return TransactionRiskResponse.from(transaction, indicators);
    }

    private String normalizeBeneficiary(String beneficiaryIdentifier) {
        String normalized = Normalizer.normalize(beneficiaryIdentifier, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty() || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BENEFICIARY_IDENTIFIER",
                    "beneficiaryIdentifier contains unsupported characters.");
        }
        return normalized.toLowerCase(Locale.ROOT);
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

    private ApiException transactionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction simulation was not found.");
    }
}
