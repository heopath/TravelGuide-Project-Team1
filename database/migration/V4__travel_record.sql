-- All My Trips Flyway migration: V4__travel_record
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
-- The database schema must be created before Flyway runs.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE travel_records (
    travel_record_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Travel record identifier',
    trip_id          BIGINT UNSIGNED NOT NULL COMMENT 'Completed trip identifier',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT 'Record author user identifier',
    title            VARCHAR(150) NOT NULL COMMENT 'Travel record title',
    content          TEXT NOT NULL COMMENT 'Travel record content',
    rating           DECIMAL(2,1) NULL COMMENT 'Trip rating from 0 to 5',
    visibility       VARCHAR(20) NOT NULL DEFAULT 'PRIVATE' COMMENT 'Visibility (PRIVATE, PUBLIC)',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    deleted_at       DATETIME(6) NULL COMMENT 'Soft deletion time',
    PRIMARY KEY (travel_record_id),
    UNIQUE KEY uk_travel_records_trip_user (trip_id, user_id),
    KEY idx_travel_records_user_created (user_id, created_at),
    KEY idx_travel_records_visibility_created (visibility, created_at),
    CONSTRAINT fk_travel_records_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id),
    CONSTRAINT fk_travel_records_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_travel_records_rating CHECK (rating IS NULL OR rating BETWEEN 0 AND 5),
    CONSTRAINT ck_travel_records_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Travel reviews for completed trips';

CREATE TABLE travel_record_images (
    travel_record_image_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Travel record image identifier',
    travel_record_id       BIGINT UNSIGNED NOT NULL COMMENT 'Travel record identifier',
    image_url              VARCHAR(1000) NOT NULL COMMENT 'External image URL',
    alt_text               VARCHAR(300) NULL COMMENT 'Image alternative text',
    sort_order             INT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Display order within the record',
    is_cover               BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether this is the cover image',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (travel_record_image_id),
    UNIQUE KEY uk_record_images_order (travel_record_id, sort_order),
    KEY idx_record_images_record (travel_record_id),
    CONSTRAINT fk_record_images_record FOREIGN KEY (travel_record_id) REFERENCES travel_records (travel_record_id) ON DELETE CASCADE,
    CONSTRAINT ck_record_images_order CHECK (sort_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='External images attached to travel records';
