-- All My Trips Flyway migration: V6__admin_operation
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
-- The database schema must be created before Flyway runs.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE place_sync_jobs (
    place_sync_job_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Place synchronization job identifier',
    requested_by      BIGINT UNSIGNED NULL COMMENT 'Administrator user identifier; NULL for scheduled jobs',
    provider          VARCHAR(30) NOT NULL COMMENT 'External place data provider name',
    job_type          VARCHAR(20) NOT NULL COMMENT 'Synchronization type (FULL, INCREMENTAL, MANUAL)',
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Job status (PENDING, RUNNING, SUCCEEDED, PARTIAL_FAILED, FAILED)',
    requested_count   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of source records requested or discovered',
    processed_count   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of source records processed',
    created_count     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of places created',
    updated_count     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of places updated',
    failed_count      INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of records that failed',
    error_summary     VARCHAR(1000) NULL COMMENT 'Summary of job-level failure',
    started_at        DATETIME(6) NULL COMMENT 'Job start time',
    completed_at      DATETIME(6) NULL COMMENT 'Job completion time',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Job request creation time',
    PRIMARY KEY (place_sync_job_id),
    KEY idx_place_sync_jobs_status_created (status, created_at),
    KEY idx_place_sync_jobs_provider_created (provider, created_at),
    KEY idx_place_sync_jobs_requested_by (requested_by),
    CONSTRAINT fk_place_sync_jobs_requester FOREIGN KEY (requested_by) REFERENCES users (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_place_sync_jobs_type CHECK (job_type IN ('FULL', 'INCREMENTAL', 'MANUAL')),
    CONSTRAINT ck_place_sync_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED')),
    CONSTRAINT ck_place_sync_jobs_counts CHECK (processed_count <= requested_count AND created_count + updated_count + failed_count <= processed_count),
    CONSTRAINT ck_place_sync_jobs_period CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
) ENGINE=InnoDB COMMENT='External place data synchronization job history';

CREATE TABLE place_sync_errors (
    place_sync_error_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Place synchronization error identifier',
    place_sync_job_id   BIGINT UNSIGNED NOT NULL COMMENT 'Synchronization job identifier',
    external_place_id   VARCHAR(100) NULL COMMENT 'Failed source place identifier',
    error_code          VARCHAR(100) NULL COMMENT 'Machine-readable error code',
    error_message       VARCHAR(1000) NOT NULL COMMENT 'Error description',
    raw_payload         JSON NULL COMMENT 'Failed source payload for diagnosis and retry',
    retry_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Retry status (PENDING, RETRIED, RESOLVED, IGNORED)',
    retry_count         SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Number of retry attempts',
    last_retried_at     DATETIME(6) NULL COMMENT 'Most recent retry time',
    resolved_at         DATETIME(6) NULL COMMENT 'Resolution time',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (place_sync_error_id),
    KEY idx_place_sync_errors_job_status (place_sync_job_id, retry_status),
    KEY idx_place_sync_errors_external (external_place_id),
    CONSTRAINT fk_place_sync_errors_job FOREIGN KEY (place_sync_job_id) REFERENCES place_sync_jobs (place_sync_job_id) ON DELETE CASCADE,
    CONSTRAINT ck_place_sync_errors_retry_status CHECK (retry_status IN ('PENDING', 'RETRIED', 'RESOLVED', 'IGNORED'))
) ENGINE=InnoDB COMMENT='Record-level failures from place synchronization jobs';

CREATE TABLE admin_audit_logs (
    admin_audit_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Administrator audit log identifier',
    admin_user_id      BIGINT UNSIGNED NULL COMMENT 'Administrator user identifier; NULL for system actions',
    action_type        VARCHAR(30) NOT NULL COMMENT 'Administrative action type',
    target_type        VARCHAR(50) NOT NULL COMMENT 'Changed entity or resource type',
    target_id          VARCHAR(100) NULL COMMENT 'Changed entity identifier',
    before_data        JSON NULL COMMENT 'Data snapshot before the change',
    after_data         JSON NULL COMMENT 'Data snapshot after the change',
    request_id         VARCHAR(100) NULL COMMENT 'Request correlation identifier',
    ip_address         VARCHAR(45) NULL COMMENT 'Administrator IPv4 or IPv6 address',
    user_agent         VARCHAR(500) NULL COMMENT 'Administrator client user agent',
    occurred_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Action occurrence time',
    PRIMARY KEY (admin_audit_log_id),
    KEY idx_admin_audit_logs_admin_occurred (admin_user_id, occurred_at),
    KEY idx_admin_audit_logs_target (target_type, target_id, occurred_at),
    KEY idx_admin_audit_logs_request (request_id),
    CONSTRAINT fk_admin_audit_logs_admin FOREIGN KEY (admin_user_id) REFERENCES users (user_id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='Immutable administrator and system action audit trail';
