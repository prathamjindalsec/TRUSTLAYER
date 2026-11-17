package com.trustlayer.entity;

import com.trustlayer.entity.enums.ConfidenceLevel;
import com.trustlayer.entity.enums.RiskClassification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One explainable result is retained for each scan. Risk scores use a Java byte because the
 * Flyway schema intentionally constrains the persisted range to MySQL TINYINT UNSIGNED.
 */
@Entity
@Table(name = "scan_results")
public class ScanResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false, unique = true)
    private Scan scan;

    @Column(name = "risk_score", nullable = false)
    private byte riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_classification", nullable = false, length = 16)
    private RiskClassification riskClassification;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 16)
    private ConfidenceLevel confidenceLevel;

    @Column(name = "evidence_limitations", length = 1000)
    private String evidenceLimitations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> explanation = new LinkedHashMap<>();

    @Column(name = "analyzer_version", nullable = false, length = 100)
    private String analyzerVersion;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScanResult() {
    }

    public ScanResult(
            Scan scan,
            int riskScore,
            RiskClassification riskClassification,
            ConfidenceLevel confidenceLevel,
            String evidenceLimitations,
            Map<String, Object> explanation,
            String analyzerVersion,
            Instant analyzedAt
    ) {
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100.");
        }
        this.scan = scan;
        this.riskScore = (byte) riskScore;
        this.riskClassification = riskClassification;
        this.confidenceLevel = confidenceLevel;
        this.evidenceLimitations = evidenceLimitations;
        this.explanation = new LinkedHashMap<>(explanation);
        this.analyzerVersion = analyzerVersion;
        this.analyzedAt = analyzedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getScanId() {
        return scan.getId();
    }

    public int getRiskScore() {
        return Byte.toUnsignedInt(riskScore);
    }

    public RiskClassification getRiskClassification() {
        return riskClassification;
    }

    public ConfidenceLevel getConfidenceLevel() {
        return confidenceLevel;
    }

    public String getEvidenceLimitations() {
        return evidenceLimitations;
    }

    public Map<String, Object> getExplanation() {
        return new LinkedHashMap<>(explanation);
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
