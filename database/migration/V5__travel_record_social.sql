-- All My Trips Flyway migration: V5__travel_record_social
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
-- The database schema must be created before Flyway runs.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE travel_record_comments (
    travel_record_comment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Travel record comment identifier',
    travel_record_id         BIGINT UNSIGNED NOT NULL COMMENT 'Travel record identifier',
    user_id                  BIGINT UNSIGNED NOT NULL COMMENT 'Comment author user identifier',
    parent_comment_id        BIGINT UNSIGNED NULL COMMENT 'Parent comment identifier for replies',
    content                  VARCHAR(1000) NOT NULL COMMENT 'Comment content',
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Comment status (ACTIVE, HIDDEN, DELETED)',
    created_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    deleted_at               DATETIME(6) NULL COMMENT 'Soft deletion time',
    PRIMARY KEY (travel_record_comment_id),
    KEY idx_record_comments_record_created (travel_record_id, created_at),
    KEY idx_record_comments_user (user_id),
    KEY idx_record_comments_parent (parent_comment_id),
    CONSTRAINT fk_record_comments_record FOREIGN KEY (travel_record_id) REFERENCES travel_records (travel_record_id) ON DELETE CASCADE,
    CONSTRAINT fk_record_comments_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_record_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES travel_record_comments (travel_record_comment_id) ON DELETE SET NULL,
    CONSTRAINT ck_record_comments_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Comments and replies on travel records';

CREATE TABLE travel_record_likes (
    travel_record_like_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Travel record like identifier',
    travel_record_id      BIGINT UNSIGNED NOT NULL COMMENT 'Travel record identifier',
    user_id               BIGINT UNSIGNED NOT NULL COMMENT 'User identifier',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (travel_record_like_id),
    UNIQUE KEY uk_record_likes_record_user (travel_record_id, user_id),
    KEY idx_record_likes_user_created (user_id, created_at),
    CONSTRAINT fk_record_likes_record FOREIGN KEY (travel_record_id) REFERENCES travel_records (travel_record_id) ON DELETE CASCADE,
    CONSTRAINT fk_record_likes_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User likes on travel records';

CREATE TABLE travel_record_shares (
    travel_record_share_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Travel record share event identifier',
    travel_record_id       BIGINT UNSIGNED NOT NULL COMMENT 'Travel record identifier',
    user_id                BIGINT UNSIGNED NULL COMMENT 'Sharing user identifier; NULL for anonymous share',
    channel                VARCHAR(30) NOT NULL COMMENT 'Share channel (LINK, KAKAO, FACEBOOK, X, OTHER)',
    shared_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Share time',
    PRIMARY KEY (travel_record_share_id),
    KEY idx_record_shares_record_time (travel_record_id, shared_at),
    KEY idx_record_shares_user_time (user_id, shared_at),
    CONSTRAINT fk_record_shares_record FOREIGN KEY (travel_record_id) REFERENCES travel_records (travel_record_id) ON DELETE CASCADE,
    CONSTRAINT fk_record_shares_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_record_shares_channel CHECK (channel IN ('LINK', 'KAKAO', 'FACEBOOK', 'X', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Accumulated travel record share events';

CREATE TABLE travel_record_reports (
    travel_record_report_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Travel record report identifier',
    travel_record_id        BIGINT UNSIGNED NOT NULL COMMENT 'Reported travel record identifier',
    reporter_user_id        BIGINT UNSIGNED NOT NULL COMMENT 'Reporting user identifier',
    reason                  VARCHAR(30) NOT NULL COMMENT 'Report reason',
    detail                  VARCHAR(1000) NULL COMMENT 'Detailed report description',
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Report status (PENDING, REVIEWING, RESOLVED, REJECTED)',
    processed_by            BIGINT UNSIGNED NULL COMMENT 'Administrator processor user identifier',
    processed_at            DATETIME(6) NULL COMMENT 'Processing completion time',
    resolution_note         VARCHAR(1000) NULL COMMENT 'Administrator resolution note',
    created_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (travel_record_report_id),
    KEY idx_record_reports_status_created (status, created_at),
    KEY idx_record_reports_record_status (travel_record_id, status),
    KEY idx_record_reports_reporter (reporter_user_id),
    CONSTRAINT fk_record_reports_record FOREIGN KEY (travel_record_id) REFERENCES travel_records (travel_record_id) ON DELETE CASCADE,
    CONSTRAINT fk_record_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_record_reports_processor FOREIGN KEY (processed_by) REFERENCES users (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_record_reports_reason CHECK (reason IN ('SPAM', 'ABUSE', 'INAPPROPRIATE', 'COPYRIGHT', 'PRIVACY', 'OTHER')),
    CONSTRAINT ck_record_reports_status CHECK (status IN ('PENDING', 'REVIEWING', 'RESOLVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Travel record reports and administrator resolutions';
