package com.trustlayer.entity;

import com.trustlayer.entity.enums.ThreatCategory;
import com.trustlayer.entity.enums.ThreatReportStatus;
import com.trustlayer.entity.enums.ThreatType;
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

/** A single user submission, retained as unverified input rather than a fraud finding. */
@Entity
@Table(name = "reports")
public class ThreatReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private UserAccount reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threat_id")
    private Threat threat;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 16)
    private ThreatType indicatorType;

    @Column(name = "indicator_value", nullable = false, length = 2048)
    private String indicatorValue;

    @Column(name = "indicator_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String indicatorHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ThreatCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 16)
    private ThreatReportStatus reportStatus;

    @Column(length = 4000)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ThreatReport() {
    }

    public ThreatReport(
            UserAccount reporter,
            Threat threat,
            ThreatType indicatorType,
            String safeIndicatorValue,
            String indicatorHash,
            ThreatCategory category,
            String safeDescription
    ) {
        this.reporter = reporter;
        this.threat = threat;
        this.indicatorType = indicatorType;
        this.indicatorValue = safeIndicatorValue;
        this.indicatorHash = indicatorHash;
        this.category = category;
        this.description = safeDescription;
        this.reportStatus = ThreatReportStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Threat getThreat() {
        return threat;
    }

    public String getIndicatorValue() {
        return indicatorValue;
    }

    public ThreatCategory getCategory() {
        return category;
    }

    public ThreatReportStatus getReportStatus() {
        return reportStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Restricted moderator-facing context only. This entity is never serialized as a public API
     * response, and the canonicalizer redacts the submitted indicator before this field is set.
     */
    public String getDescription() {
        return description;
    }
}
