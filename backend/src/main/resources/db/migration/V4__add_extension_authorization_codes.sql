-- Phase 11: one-time, PKCE-bound authorization codes for the Chrome extension.
-- The raw authorization code is returned once to the browser but only its SHA-256 hash is stored.
CREATE TABLE extension_authorization_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    redirect_uri VARCHAR(1024) NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_challenge VARCHAR(128) NOT NULL,
    scope VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_extension_authorization_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_extension_authorization_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    KEY idx_extension_authorization_expiry (expires_at),
    KEY idx_extension_authorization_user_created (user_id, created_at DESC)
) ENGINE=InnoDB;
