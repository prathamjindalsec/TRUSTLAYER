package com.trustlayer.entity;

import com.trustlayer.entity.enums.ScanStatus;
import com.trustlayer.entity.enums.ScanType;
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
 * A user-owned scan request. The persisted target is deliberately privacy-redacted by the
 * URL analyzer: credentials, path values, query values, and fragments never reach this table.
 */
@Entity
@Table(name = "scans")
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false, length = 16)
    private ScanType scanType;

    @Column(name = "target_value", nullable = false, length = 2048)
    private String targetValue;

    @Column(name = "target_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String targetHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Scan() {
    }

    public Scan(UserAccount user, ScanType scanType, String targetValue, String targetHash, Instant requestedAt) {
        this.user = user;
        this.scanType = scanType;
        this.targetValue = targetValue;
        this.targetHash = targetHash;
        this.requestedAt = requestedAt;
        this.status = ScanStatus.QUEUED;
    }

    public void markProcessing() {
        status = ScanStatus.PROCESSING;
        failureCode = null;
    }

    public void markCompleted(Instant completedAt) {
        status = ScanStatus.COMPLETED;
        this.completedAt = completedAt;
        failureCode = null;
    }

    public void markFailed(String code, Instant completedAt) {
        status = ScanStatus.FAILED;
        failureCode = code;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public ScanType getScanType() {
        return scanType;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public String getTargetHash() {
        return targetHash;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
