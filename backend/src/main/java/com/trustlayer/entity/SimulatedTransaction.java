package com.trustlayer.entity;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.TransactionAnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A user-entered simulation, not a payment instruction or a record received from a bank. The
 * beneficiary display field contains a redacted label; matching uses a one-way hash instead.
 */
@Entity
@Table(name = "transactions")
public class SimulatedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(name = "external_reference", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String externalReference;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "beneficiary_identifier", nullable = false, length = 255)
    private String beneficiaryIdentifier;

    @Column(name = "beneficiary_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String beneficiaryHash;

    @Column(name = "transaction_time", nullable = false)
    private Instant transactionTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 16)
    private TransactionAnalysisStatus analysisStatus;

    @Column(name = "risk_score")
    private Byte riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_classification", length = 16)
    private RiskClassification riskClassification;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 16)
    private ConfidenceLevel confidenceLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> explanation;

    @Column(nullable = false)
    private boolean simulated;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SimulatedTransaction() {
    }

    public SimulatedTransaction(
            UserAccount user,
            String externalReference,
            BigDecimal amount,
            String currency,
            String redactedBeneficiaryIdentifier,
            String beneficiaryHash,
            Instant transactionTime
    ) {
        this.user = user;
        this.externalReference = externalReference;
        this.amount = amount;
        this.currency = currency;
        this.beneficiaryIdentifier = redactedBeneficiaryIdentifier;
        this.beneficiaryHash = beneficiaryHash;
        this.transactionTime = transactionTime;
        this.analysisStatus = TransactionAnalysisStatus.PENDING;
        this.simulated = true;
    }

    public void markCompleted(
            int calculatedRiskScore,
            RiskClassification calculatedRiskClassification,
            ConfidenceLevel calculatedConfidenceLevel,
            Map<String, Object> calculatedExplanation
    ) {
        if (calculatedRiskScore < 0 || calculatedRiskScore > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100.");
        }
        riskScore = (byte) calculatedRiskScore;
        riskClassification = calculatedRiskClassification;
        confidenceLevel = calculatedConfidenceLevel;
        explanation = new LinkedHashMap<>(calculatedExplanation);
        analysisStatus = TransactionAnalysisStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBeneficiaryIdentifier() {
        return beneficiaryIdentifier;
    }

    public String getBeneficiaryHash() {
        return beneficiaryHash;
    }

    public Instant getTransactionTime() {
        return transactionTime;
    }

    public TransactionAnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public Integer getRiskScore() {
        return riskScore == null ? null : Byte.toUnsignedInt(riskScore);
    }

    public RiskClassification getRiskClassification() {
        return riskClassification;
    }

    public ConfidenceLevel getConfidenceLevel() {
        return confidenceLevel;
    }

    public Map<String, Object> getExplanation() {
        return explanation == null ? Map.of() : new LinkedHashMap<>(explanation);
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
