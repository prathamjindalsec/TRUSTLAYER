package com.trustlayer.entity;

import com.trustlayer.entity.enums.IndicatorSeverity;
import com.trustlayer.entity.enums.RiskIndicatorType;
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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An individually weighted piece of evidence. Phase 4 only uses scan_result_id; transaction
 * evidence is intentionally deferred until the transaction-analysis module exists.
 */
@Entity
@Table(name = "risk_indicators")
public class RiskIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_result_id")
    private ScanResult scanResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private SimulatedTransaction transaction;

    @Column(name = "indicator_code", nullable = false, length = 100)
    private String indicatorCode;

    @Column(name = "indicator_name", nullable = false, length = 255)
    private String indicatorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 32)
    private RiskIndicatorType indicatorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IndicatorSeverity severity;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> evidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RiskIndicator() {
    }

    public RiskIndicator(
            ScanResult scanResult,
            String indicatorCode,
            String indicatorName,
            RiskIndicatorType indicatorType,
            IndicatorSeverity severity,
            BigDecimal weight,
            Map<String, Object> evidence
    ) {
        this.scanResult = scanResult;
        this.transaction = null;
        this.indicatorCode = indicatorCode;
        this.indicatorName = indicatorName;
        this.indicatorType = indicatorType;
        this.severity = severity;
        this.weight = weight;
        this.evidence = evidence == null ? null : new LinkedHashMap<>(evidence);
    }

    public RiskIndicator(
            SimulatedTransaction transaction,
            String indicatorCode,
            String indicatorName,
            RiskIndicatorType indicatorType,
            IndicatorSeverity severity,
            BigDecimal weight,
            Map<String, Object> evidence
    ) {
        this.scanResult = null;
        this.transaction = transaction;
        this.indicatorCode = indicatorCode;
        this.indicatorName = indicatorName;
        this.indicatorType = indicatorType;
        this.severity = severity;
        this.weight = weight;
        this.evidence = evidence == null ? null : new LinkedHashMap<>(evidence);
    }

    public Long getId() {
        return id;
    }

    public String getIndicatorCode() {
        return indicatorCode;
    }

    public String getIndicatorName() {
        return indicatorName;
    }

    public RiskIndicatorType getIndicatorType() {
        return indicatorType;
    }

    public IndicatorSeverity getSeverity() {
        return severity;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public Map<String, Object> getEvidence() {
        return evidence == null ? Map.of() : new LinkedHashMap<>(evidence);
    }
}
