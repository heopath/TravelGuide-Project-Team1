-- All My Trips Flyway migration: V1__member_and_place
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
-- The database schema must be created before Flyway runs.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE users (
    user_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'User identifier',
    email           VARCHAR(255) NOT NULL COMMENT 'Login email address',
    password_hash   VARCHAR(255) NOT NULL COMMENT 'Encoded password',
    nickname        VARCHAR(50) NOT NULL COMMENT 'Display nickname',
    role            VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'Authorization role (USER, ADMIN)',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Account status (ACTIVE, SUSPENDED, WITHDRAWN)',
    last_login_at   DATETIME(6) NULL COMMENT 'Most recent login time',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    deleted_at      DATETIME(6) NULL COMMENT 'Soft deletion time',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_nickname (nickname),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'))
) ENGINE=InnoDB COMMENT='Member accounts';

CREATE TABLE travel_styles (
    travel_style_id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Travel style identifier',
    code             VARCHAR(30) NOT NULL COMMENT 'Stable style code',
    name             VARCHAR(50) NOT NULL COMMENT 'Style display name',
    description      VARCHAR(255) NULL COMMENT 'Style description',
    is_active        BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether the style can be selected',
    sort_order       SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Display order',
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (travel_style_id),
    UNIQUE KEY uk_travel_styles_code (code),
    UNIQUE KEY uk_travel_styles_name (name)
) ENGINE=InnoDB COMMENT='Travel style code dictionary';

CREATE TABLE user_preferences (
    user_preference_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'User preference identifier',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT 'User identifier',
    travel_style_id     SMALLINT UNSIGNED NOT NULL COMMENT 'Travel style identifier',
    preference_score    TINYINT UNSIGNED NOT NULL DEFAULT 50 COMMENT 'Preference score from 0 to 100',
    source               VARCHAR(20) NOT NULL DEFAULT 'EXPLICIT' COMMENT 'Preference source (EXPLICIT, INFERRED)',
    created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (user_preference_id),
    UNIQUE KEY uk_user_preferences_user_style (user_id, travel_style_id),
    KEY idx_user_preferences_style (travel_style_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_preferences_style FOREIGN KEY (travel_style_id) REFERENCES travel_styles (travel_style_id),
    CONSTRAINT ck_user_preferences_score CHECK (preference_score BETWEEN 0 AND 100),
    CONSTRAINT ck_user_preferences_source CHECK (source IN ('EXPLICIT', 'INFERRED'))
) ENGINE=InnoDB COMMENT='Per-user travel style preferences';

CREATE TABLE places (
    place_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Place identifier',
    external_provider  VARCHAR(30) NULL COMMENT 'External data provider name',
    external_place_id  VARCHAR(100) NULL COMMENT 'Place identifier at the provider',
    category           VARCHAR(30) NOT NULL COMMENT 'Place category (ATTRACTION, RESTAURANT, CAFE, ACCOMMODATION, FESTIVAL, ACTIVITY, TRANSPORT)',
    name               VARCHAR(150) NOT NULL COMMENT 'Place name',
    country_code       CHAR(2) NOT NULL DEFAULT 'KR' COMMENT 'ISO 3166-1 alpha-2 country code',
    region             VARCHAR(100) NOT NULL COMMENT 'State or province',
    city               VARCHAR(100) NULL COMMENT 'City or district',
    address            VARCHAR(255) NULL COMMENT 'Street address',
    latitude           DECIMAL(10,7) NULL COMMENT 'Latitude',
    longitude          DECIMAL(10,7) NULL COMMENT 'Longitude',
    description        TEXT NULL COMMENT 'Place description',
    phone              VARCHAR(30) NULL COMMENT 'Contact phone number',
    website_url        VARCHAR(500) NULL COMMENT 'Official website URL',
    average_rating     DECIMAL(3,2) NULL COMMENT 'Average rating from 0 to 5',
    is_active          BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether the place is exposed',
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (place_id),
    UNIQUE KEY uk_places_provider_external (external_provider, external_place_id),
    KEY idx_places_region_city_category (region, city, category),
    KEY idx_places_location (latitude, longitude),
    CONSTRAINT ck_places_category CHECK (category IN ('ATTRACTION', 'RESTAURANT', 'CAFE', 'ACCOMMODATION', 'FESTIVAL', 'ACTIVITY', 'TRANSPORT')),
    CONSTRAINT ck_places_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_places_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_places_rating CHECK (average_rating IS NULL OR average_rating BETWEEN 0 AND 5)
) ENGINE=InnoDB COMMENT='Searchable travel places';

CREATE TABLE place_images (
    place_image_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Place image identifier',
    place_id       BIGINT UNSIGNED NOT NULL COMMENT 'Place identifier',
    image_url      VARCHAR(1000) NOT NULL COMMENT 'Image URL',
    alt_text       VARCHAR(255) NULL COMMENT 'Image alternative text',
    sort_order     SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Display order',
    is_primary     BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether this is the primary image',
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (place_image_id),
    KEY idx_place_images_place_order (place_id, sort_order),
    CONSTRAINT fk_place_images_place FOREIGN KEY (place_id) REFERENCES places (place_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Place image metadata';

CREATE TABLE place_travel_styles (
    place_travel_style_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Place travel style identifier',
    place_id              BIGINT UNSIGNED NOT NULL COMMENT 'Place identifier',
    travel_style_id       SMALLINT UNSIGNED NOT NULL COMMENT 'Travel style identifier',
    relevance_score       DECIMAL(5,2) NOT NULL DEFAULT 50.00 COMMENT 'Style relevance score from 0 to 100',
    source                VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'Tag source (MANUAL, AI, BEHAVIOR)',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (place_travel_style_id),
    UNIQUE KEY uk_place_travel_styles_place_style (place_id, travel_style_id),
    KEY idx_place_travel_styles_style_score (travel_style_id, relevance_score),
    CONSTRAINT fk_place_travel_styles_place FOREIGN KEY (place_id) REFERENCES places (place_id) ON DELETE CASCADE,
    CONSTRAINT fk_place_travel_styles_style FOREIGN KEY (travel_style_id) REFERENCES travel_styles (travel_style_id),
    CONSTRAINT ck_place_travel_styles_score CHECK (relevance_score BETWEEN 0 AND 100),
    CONSTRAINT ck_place_travel_styles_source CHECK (source IN ('MANUAL', 'AI', 'BEHAVIOR'))
) ENGINE=InnoDB COMMENT='Travel style relevance tags for places';

CREATE TABLE favorites (
    favorite_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Favorite identifier',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT 'User identifier',
    place_id    BIGINT UNSIGNED NOT NULL COMMENT 'Place identifier',
    memo        VARCHAR(500) NULL COMMENT 'User memo for the favorite',
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_favorites_user_place (user_id, place_id),
    KEY idx_favorites_place (place_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_place FOREIGN KEY (place_id) REFERENCES places (place_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Places saved by users';

