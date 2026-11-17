-- TRUSTLAYER Phase 2 baseline schema.
-- This migration is immutable once it has been applied. Future corrections use a new versioned migration.

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NULL,
    last_name VARCHAR(100) NULL,
    role ENUM('USER', 'ANALYST', 'ADMIN') NOT NULL DEFAULT 'USER',
    status ENUM('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED') NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    KEY idx_users_status_created (status, created_at),
    KEY idx_users_deleted_at (deleted_at)
) ENGINE=InnoDB;

CREATE TABLE user_preferences (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    theme ENUM('SYSTEM', 'LIGHT', 'DARK') NOT NULL DEFAULT 'SYSTEM',
    locale VARCHAR(16) NOT NULL DEFAULT 'en',
    notification_preferences JSON NULL,
    privacy_preferences JSON NULL,
    cookie_preferences JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_user_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE authentication_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ip_address VARBINARY(16) NULL,
    user_agent VARCHAR(512) NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    last_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_sessions_session_id UNIQUE (session_id),
    CONSTRAINT uq_sessions_token_hash UNIQUE (session_token_hash),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    KEY idx_sessions_user_active (user_id, revoked_at, expires_at),
    KEY idx_sessions_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE threats (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    threat_type ENUM('URL', 'DOMAIN', 'PHONE', 'QR_PAYLOAD', 'BENEFICIARY', 'OTHER') NOT NULL,
    indicator_value VARCHAR(2048) NOT NULL,
    indicator_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    category ENUM('PHISHING', 'MALWARE', 'PAYMENT_SCAM', 'IMPERSONATION', 'SOCIAL_ENGINEERING', 'OTHER') NOT NULL,
    verification_status ENUM('UNVERIFIED', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'UNVERIFIED',
    confidence_score TINYINT UNSIGNED NULL,
    report_count INT UNSIGNED NOT NULL DEFAULT 0,
    first_reported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_reported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    verified_at DATETIME(6) NULL,
    verified_by_user_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_threats_indicator UNIQUE (threat_type, indicator_hash),
    CONSTRAINT chk_threat_confidence CHECK (confidence_score IS NULL OR confidence_score BETWEEN 0 AND 100),
    CONSTRAINT fk_threats_verifier FOREIGN KEY (verified_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    KEY idx_threats_status_category (verification_status, category),
    KEY idx_threats_report_count (report_count DESC),
    KEY idx_threats_last_reported (last_reported_at DESC)
) ENGINE=InnoDB;

CREATE TABLE scans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    scan_type ENUM('URL', 'QR') NOT NULL,
    target_value VARCHAR(2048) NOT NULL,
    target_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status ENUM('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'QUEUED',
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    failure_code VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_scans_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    KEY idx_scans_user_requested (user_id, requested_at DESC),
    KEY idx_scans_type_status_requested (scan_type, status, requested_at DESC),
    KEY idx_scans_target_hash (target_hash)
) ENGINE=InnoDB;

CREATE TABLE scan_results (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scan_id BIGINT UNSIGNED NOT NULL,
    risk_score TINYINT UNSIGNED NOT NULL,
    risk_classification ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL,
    confidence_level ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL DEFAULT 'LOW',
    evidence_limitations VARCHAR(1000) NULL,
    explanation JSON NOT NULL,
    analyzer_version VARCHAR(100) NOT NULL DEFAULT 'rules-v1',
    analyzed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_scan_results_scan UNIQUE (scan_id),
    CONSTRAINT chk_scan_result_score CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT fk_scan_results_scan FOREIGN KEY (scan_id) REFERENCES scans (id) ON DELETE CASCADE,
    KEY idx_scan_results_risk (risk_classification, risk_score DESC),
    KEY idx_scan_results_analyzed (analyzed_at DESC)
) ENGINE=InnoDB;

CREATE TABLE transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    external_reference CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    beneficiary_identifier VARCHAR(255) NOT NULL,
    beneficiary_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    transaction_time DATETIME(6) NOT NULL,
    analysis_status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    risk_score TINYINT UNSIGNED NULL,
    risk_classification ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NULL,
    explanation JSON NULL,
    simulated BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_transactions_reference UNIQUE (external_reference),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_risk CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    KEY idx_transactions_user_time (user_id, transaction_time DESC),
    KEY idx_transactions_beneficiary_time (beneficiary_hash, transaction_time DESC),
    KEY idx_transactions_risk (risk_classification, risk_score DESC)
) ENGINE=InnoDB;

CREATE TABLE risk_indicators (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scan_result_id BIGINT UNSIGNED NULL,
    transaction_id BIGINT UNSIGNED NULL,
    indicator_code VARCHAR(100) NOT NULL,
    indicator_name VARCHAR(255) NOT NULL,
    indicator_type ENUM('CONFIRMED_MALICIOUS', 'SUSPICIOUS', 'UNKNOWN') NOT NULL,
    severity ENUM('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL,
    weight DECIMAL(6, 2) NOT NULL,
    evidence JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_indicator_subject CHECK (
        (scan_result_id IS NOT NULL AND transaction_id IS NULL)
        OR (scan_result_id IS NULL AND transaction_id IS NOT NULL)
    ),
    CONSTRAINT fk_indicators_scan_result FOREIGN KEY (scan_result_id) REFERENCES scan_results (id) ON DELETE CASCADE,
    CONSTRAINT fk_indicators_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    KEY idx_indicators_scan_result (scan_result_id),
    KEY idx_indicators_transaction (transaction_id),
    KEY idx_indicators_code (indicator_code)
) ENGINE=InnoDB;

CREATE TABLE reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT UNSIGNED NULL,
    threat_id BIGINT UNSIGNED NULL,
    indicator_type ENUM('URL', 'DOMAIN', 'PHONE', 'QR_PAYLOAD', 'BENEFICIARY', 'OTHER') NOT NULL,
    indicator_value VARCHAR(2048) NOT NULL,
    indicator_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    category ENUM('PHISHING', 'MALWARE', 'PAYMENT_SCAM', 'IMPERSONATION', 'SOCIAL_ENGINEERING', 'OTHER') NOT NULL,
    report_status ENUM('PENDING', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED', 'DUPLICATE') NOT NULL DEFAULT 'PENDING',
    description VARCHAR(4000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_reports_reporter_indicator UNIQUE (reporter_user_id, indicator_type, indicator_hash),
    CONSTRAINT fk_reports_user FOREIGN KEY (reporter_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_reports_threat FOREIGN KEY (threat_id) REFERENCES threats (id) ON DELETE RESTRICT,
    KEY idx_reports_threat_status (threat_id, report_status),
    KEY idx_reports_status_created (report_status, created_at DESC)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NULL,
    outcome ENUM('SUCCESS', 'FAILURE', 'DENIED') NOT NULL,
    ip_address VARBINARY(16) NULL,
    user_agent VARCHAR(512) NULL,
    context JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    KEY idx_audit_actor_created (actor_user_id, created_at DESC),
    KEY idx_audit_entity_created (entity_type, entity_id, created_at DESC),
    KEY idx_audit_event_created (event_type, created_at DESC)
) ENGINE=InnoDB;
