package com.trustlayer.entity;

import com.trustlayer.entity.enums.ThreatCategory;
import com.trustlayer.entity.enums.ThreatType;
import com.trustlayer.entity.enums.ThreatVerificationStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Canonical, privacy-redacted community indicator. Its verification status is separate from the
 * number of reports: corroboration is not proof of maliciousness.
 */
@Entity
@Table(name = "threats")
public class Threat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_type", nullable = false, length = 16)
    private ThreatType threatType;

    @Column(name = "indicator_value", nullable = false, length = 2048)
    private String indicatorValue;

    @Column(name = "indicator_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String indicatorHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ThreatCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16)
    private ThreatVerificationStatus verificationStatus;

    @Column(name = "confidence_score")
    private Byte confidenceScore;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "first_reported_at", nullable = false)
    private Instant firstReportedAt;

    @Column(name = "last_reported_at", nullable = false)
    private Instant lastReportedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private UserAccount verifiedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Threat() {
    }

    public Threat(ThreatType threatType, String safeIndicatorValue, String indicatorHash, ThreatCategory category, Instant now) {
        this.threatType = threatType;
        this.indicatorValue = safeIndicatorValue;
        this.indicatorHash = indicatorHash;
        this.category = category;
        this.verificationStatus = ThreatVerificationStatus.UNVERIFIED;
        this.reportCount = 0;
        this.firstReportedAt = now;
        this.lastReportedAt = now;
    }

    public void recordReport(Instant now) {
        reportCount++;
        lastReportedAt = now;
    }

    public void updateVerification(ThreatVerificationStatus status, UserAccount verifier, Instant now) {
        verificationStatus = status;
        if (status == ThreatVerificationStatus.VERIFIED) {
            confidenceScore = (byte) 100;
            verifiedAt = now;
            verifiedBy = verifier;
        } else if (status == ThreatVerificationStatus.REJECTED) {
            confidenceScore = 0;
            verifiedAt = now;
            verifiedBy = verifier;
        } else {
            confidenceScore = null;
            verifiedAt = null;
            verifiedBy = null;
        }
    }

    public Long getId() {
        return id;
    }

    public ThreatType getThreatType() {
        return threatType;
    }

    public String getIndicatorValue() {
        return indicatorValue;
    }

    public String getIndicatorHash() {
        return indicatorHash;
    }

    public ThreatCategory getCategory() {
        return category;
    }

    public ThreatVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public Integer getConfidenceScore() {
        return confidenceScore == null ? null : Byte.toUnsignedInt(confidenceScore);
    }

    public int getReportCount() {
        return reportCount;
    }

    public Instant getFirstReportedAt() {
        return firstReportedAt;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
