-- All My Trip database schema
-- Target: MySQL 8.0+
-- Character set: utf8mb4

CREATE DATABASE IF NOT EXISTS all_my_trip
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE all_my_trip;

-- =========================================================
-- 1. Member and preference domain
-- =========================================================

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

-- =========================================================
-- 2. Place catalog domain
-- =========================================================

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

-- =========================================================
-- 3. Trip planning domain
-- =========================================================

CREATE TABLE trips (
    trip_id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Trip identifier',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT 'Owner user identifier',
    title               VARCHAR(150) NOT NULL COMMENT 'Trip title',
    destination_name    VARCHAR(150) NOT NULL COMMENT 'Destination entered by the user',
    start_date          DATE NOT NULL COMMENT 'Trip start date',
    end_date            DATE NOT NULL COMMENT 'Trip end date',
    companion_type      VARCHAR(20) NOT NULL COMMENT 'Companion type (SOLO, FRIENDS, COUPLE, FAMILY, GROUP, OTHER)',
    companion_count     SMALLINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Total traveler count',
    purpose             VARCHAR(100) NULL COMMENT 'Trip purpose',
    budget_amount       DECIMAL(15,2) NULL COMMENT 'Expected total budget',
    currency_code       CHAR(3) NOT NULL DEFAULT 'KRW' COMMENT 'ISO 4217 currency code',
    transport_preference VARCHAR(100) NULL COMMENT 'Preferred transport',
    food_preference     VARCHAR(255) NULL COMMENT 'Preferred food',
    pace                VARCHAR(20) NULL COMMENT 'Travel pace (RELAXED, NORMAL, PACKED)',
    accommodation_style VARCHAR(100) NULL COMMENT 'Preferred accommodation style',
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'Trip status (DRAFT, CONFIRMED, COMPLETED, CANCELLED)',
    source              VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'Initial creation source (MANUAL, AI)',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    deleted_at          DATETIME(6) NULL COMMENT 'Soft deletion time',
    PRIMARY KEY (trip_id),
    KEY idx_trips_user_status_start (user_id, status, start_date),
    CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_trips_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_trips_companion_type CHECK (companion_type IN ('SOLO', 'FRIENDS', 'COUPLE', 'FAMILY', 'GROUP', 'OTHER')),
    CONSTRAINT ck_trips_companion_count CHECK (companion_count > 0),
    CONSTRAINT ck_trips_budget CHECK (budget_amount IS NULL OR budget_amount >= 0),
    CONSTRAINT ck_trips_pace CHECK (pace IS NULL OR pace IN ('RELAXED', 'NORMAL', 'PACKED')),
    CONSTRAINT ck_trips_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_trips_source CHECK (source IN ('MANUAL', 'AI'))
) ENGINE=InnoDB COMMENT='User travel plans and input conditions';

CREATE TABLE trip_travel_styles (
    trip_id         BIGINT UNSIGNED NOT NULL COMMENT 'Trip identifier',
    travel_style_id SMALLINT UNSIGNED NOT NULL COMMENT 'Travel style identifier',
    priority        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Style priority; lower is preferred',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (trip_id, travel_style_id),
    KEY idx_trip_travel_styles_style (travel_style_id),
    CONSTRAINT fk_trip_travel_styles_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_travel_styles_style FOREIGN KEY (travel_style_id) REFERENCES travel_styles (travel_style_id),
    CONSTRAINT ck_trip_travel_styles_priority CHECK (priority > 0)
) ENGINE=InnoDB COMMENT='Travel styles selected for a trip';

CREATE TABLE trip_days (
    trip_day_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Trip day identifier',
    trip_id     BIGINT UNSIGNED NOT NULL COMMENT 'Trip identifier',
    day_number  SMALLINT UNSIGNED NOT NULL COMMENT 'One-based day number',
    trip_date   DATE NOT NULL COMMENT 'Calendar date of the trip day',
    title       VARCHAR(150) NULL COMMENT 'Optional day title',
    memo        TEXT NULL COMMENT 'Day memo',
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (trip_day_id),
    UNIQUE KEY uk_trip_days_trip_day_number (trip_id, day_number),
    UNIQUE KEY uk_trip_days_trip_date (trip_id, trip_date),
    CONSTRAINT fk_trip_days_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id) ON DELETE CASCADE,
    CONSTRAINT ck_trip_days_number CHECK (day_number > 0)
) ENGINE=InnoDB COMMENT='Daily sections of a trip';

CREATE TABLE itinerary_items (
    itinerary_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Itinerary item identifier',
    trip_day_id       BIGINT UNSIGNED NOT NULL COMMENT 'Trip day identifier',
    place_id          BIGINT UNSIGNED NULL COMMENT 'Linked catalog place identifier',
    item_type         VARCHAR(30) NOT NULL COMMENT 'Item type (PLACE, MEAL, ACCOMMODATION, TRANSPORT, ACTIVITY, NOTE)',
    title             VARCHAR(150) NOT NULL COMMENT 'Item title or snapshot place name',
    start_time        TIME NULL COMMENT 'Planned start time',
    end_time          TIME NULL COMMENT 'Planned end time',
    sort_order        SMALLINT UNSIGNED NOT NULL COMMENT 'Order within the day',
    memo              TEXT NULL COMMENT 'User memo',
    estimated_cost    DECIMAL(15,2) NULL COMMENT 'Estimated item cost',
    currency_code     CHAR(3) NOT NULL DEFAULT 'KRW' COMMENT 'ISO 4217 currency code',
    source            VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'Item creation source (MANUAL, AI)',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (itinerary_item_id),
    UNIQUE KEY uk_itinerary_items_day_order (trip_day_id, sort_order),
    KEY idx_itinerary_items_place (place_id),
    CONSTRAINT fk_itinerary_items_day FOREIGN KEY (trip_day_id) REFERENCES trip_days (trip_day_id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_items_place FOREIGN KEY (place_id) REFERENCES places (place_id) ON DELETE SET NULL,
    CONSTRAINT ck_itinerary_items_type CHECK (item_type IN ('PLACE', 'MEAL', 'ACCOMMODATION', 'TRANSPORT', 'ACTIVITY', 'NOTE')),
    CONSTRAINT ck_itinerary_items_time CHECK (end_time IS NULL OR start_time IS NULL OR end_time >= start_time),
    CONSTRAINT ck_itinerary_items_order CHECK (sort_order > 0),
    CONSTRAINT ck_itinerary_items_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    CONSTRAINT ck_itinerary_items_source CHECK (source IN ('MANUAL', 'AI'))
) ENGINE=InnoDB COMMENT='Ordered activities within a trip day';

-- =========================================================
-- 4. AI domain
-- =========================================================

CREATE TABLE ai_generation_requests (
    ai_generation_request_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'AI generation request identifier',
    user_id                   BIGINT UNSIGNED NOT NULL COMMENT 'Requester user identifier',
    trip_id                   BIGINT UNSIGNED NULL COMMENT 'Generated or optimized trip identifier',
    request_type              VARCHAR(30) NOT NULL COMMENT 'Request type (CREATE_ITINERARY, OPTIMIZE_ROUTE, CHAT)',
    provider                  VARCHAR(30) NOT NULL COMMENT 'AI provider name',
    model_name                VARCHAR(100) NOT NULL COMMENT 'AI model name',
    prompt_version            VARCHAR(30) NULL COMMENT 'Prompt template version',
    input_payload             JSON NOT NULL COMMENT 'Structured request input',
    output_payload            JSON NULL COMMENT 'Structured model output',
    status                    VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Processing status (PENDING, PROCESSING, SUCCEEDED, FAILED)',
    error_message             TEXT NULL COMMENT 'Failure message',
    input_tokens              INT UNSIGNED NULL COMMENT 'Input token count',
    output_tokens             INT UNSIGNED NULL COMMENT 'Output token count',
    requested_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Request time',
    completed_at              DATETIME(6) NULL COMMENT 'Completion time',
    PRIMARY KEY (ai_generation_request_id),
    KEY idx_ai_requests_user_requested (user_id, requested_at),
    KEY idx_ai_requests_trip (trip_id),
    KEY idx_ai_requests_status_requested (status, requested_at),
    CONSTRAINT fk_ai_requests_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_ai_requests_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_requests_type CHECK (request_type IN ('CREATE_ITINERARY', 'OPTIMIZE_ROUTE', 'CHAT')),
    CONSTRAINT ck_ai_requests_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_requests_completed CHECK (completed_at IS NULL OR completed_at >= requested_at)
) ENGINE=InnoDB COMMENT='AI itinerary, optimization, and chat request history';

CREATE TABLE recommendation_events (
    recommendation_event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Recommendation behavior event identifier',
    user_id                  BIGINT UNSIGNED NOT NULL COMMENT 'Acting user identifier',
    place_id                 BIGINT UNSIGNED NOT NULL COMMENT 'Recommended place identifier',
    trip_id                  BIGINT UNSIGNED NULL COMMENT 'Related trip identifier',
    ai_generation_request_id BIGINT UNSIGNED NULL COMMENT 'Related AI recommendation request identifier',
    event_type               VARCHAR(30) NOT NULL COMMENT 'Behavior type (IMPRESSION, CLICK, FAVORITE, ADD_TO_TRIP, REMOVE_FROM_TRIP, DISMISS)',
    session_id               VARCHAR(100) NULL COMMENT 'Client or recommendation session identifier',
    metadata                 JSON NULL COMMENT 'Additional event context such as rank and score',
    occurred_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Event occurrence time',
    PRIMARY KEY (recommendation_event_id),
    KEY idx_recommendation_events_user_occurred (user_id, occurred_at),
    KEY idx_recommendation_events_place_type_occurred (place_id, event_type, occurred_at),
    KEY idx_recommendation_events_trip (trip_id),
    KEY idx_recommendation_events_ai_request (ai_generation_request_id),
    KEY idx_recommendation_events_session (session_id),
    CONSTRAINT fk_recommendation_events_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_recommendation_events_place FOREIGN KEY (place_id) REFERENCES places (place_id),
    CONSTRAINT fk_recommendation_events_trip FOREIGN KEY (trip_id) REFERENCES trips (trip_id) ON DELETE SET NULL,
    CONSTRAINT fk_recommendation_events_ai_request FOREIGN KEY (ai_generation_request_id) REFERENCES ai_generation_requests (ai_generation_request_id) ON DELETE SET NULL,
    CONSTRAINT ck_recommendation_events_type CHECK (event_type IN ('IMPRESSION', 'CLICK', 'FAVORITE', 'ADD_TO_TRIP', 'REMOVE_FROM_TRIP', 'DISMISS'))
) ENGINE=InnoDB COMMENT='User behavior events for recommendation evaluation and preference inference';

-- =========================================================
-- 5. Administration and place data operation domain
-- =========================================================

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

-- =========================================================
-- 6. Ticket, reservation, and payment domain

-- =========================================================

CREATE TABLE ticket_products (
    ticket_product_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Ticket product identifier',
    place_id          BIGINT UNSIGNED NOT NULL COMMENT 'Related place identifier',
    name              VARCHAR(150) NOT NULL COMMENT 'Ticket product name',
    description       TEXT NULL COMMENT 'Ticket product description',
    sale_start_at     DATETIME(6) NOT NULL COMMENT 'Sales start time',
    sale_end_at       DATETIME(6) NOT NULL COMMENT 'Sales end time',
    usage_start_date  DATE NOT NULL COMMENT 'First usable date',
    usage_end_date    DATE NOT NULL COMMENT 'Last usable date',
    unit_price        DECIMAL(15,2) NOT NULL COMMENT 'Unit price',
    currency_code     CHAR(3) NOT NULL DEFAULT 'KRW' COMMENT 'ISO 4217 currency code',
    max_quantity_per_user SMALLINT UNSIGNED NOT NULL DEFAULT 4 COMMENT 'Maximum quantity per user',
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'Product status (DRAFT, ON_SALE, SOLD_OUT, ENDED, CANCELLED)',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (ticket_product_id),
    KEY idx_ticket_products_place_status (place_id, status),
    KEY idx_ticket_products_sale_period (sale_start_at, sale_end_at),
    CONSTRAINT fk_ticket_products_place FOREIGN KEY (place_id) REFERENCES places (place_id),
    CONSTRAINT ck_ticket_products_sale_period CHECK (sale_end_at > sale_start_at),
    CONSTRAINT ck_ticket_products_usage_period CHECK (usage_end_date >= usage_start_date),
    CONSTRAINT ck_ticket_products_price CHECK (unit_price >= 0),
    CONSTRAINT ck_ticket_products_max_quantity CHECK (max_quantity_per_user > 0),
    CONSTRAINT ck_ticket_products_status CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'ENDED', 'CANCELLED'))
) ENGINE=InnoDB COMMENT='Reservable attraction, festival, and activity tickets';

CREATE TABLE ticket_inventory (
    ticket_product_id BIGINT UNSIGNED NOT NULL COMMENT 'Ticket product identifier',
    total_quantity    INT UNSIGNED NOT NULL COMMENT 'Total sale quantity',
    reserved_quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Quantity held or sold',
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last inventory update time',
    PRIMARY KEY (ticket_product_id),
    CONSTRAINT fk_ticket_inventory_product FOREIGN KEY (ticket_product_id) REFERENCES ticket_products (ticket_product_id) ON DELETE CASCADE,
    CONSTRAINT ck_ticket_inventory_quantities CHECK (reserved_quantity <= total_quantity)
) ENGINE=InnoDB COMMENT='Ticket stock aggregate for concurrency control';

CREATE TABLE reservations (
    reservation_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Reservation identifier',
    reservation_number VARCHAR(30) NOT NULL COMMENT 'Public reservation number',
    user_id            BIGINT UNSIGNED NOT NULL COMMENT 'Reserving user identifier',
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Reservation status (PENDING, CONFIRMED, CANCELLED, EXPIRED, USED)',
    total_amount       DECIMAL(15,2) NOT NULL COMMENT 'Reservation total amount',
    currency_code      CHAR(3) NOT NULL DEFAULT 'KRW' COMMENT 'ISO 4217 currency code',
    expires_at         DATETIME(6) NULL COMMENT 'Payment hold expiration time',
    confirmed_at       DATETIME(6) NULL COMMENT 'Confirmation time',
    cancelled_at       DATETIME(6) NULL COMMENT 'Cancellation time',
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uk_reservations_number (reservation_number),
    KEY idx_reservations_user_status_created (user_id, status, created_at),
    KEY idx_reservations_status_expires (status, expires_at),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'USED')),
    CONSTRAINT ck_reservations_amount CHECK (total_amount >= 0)
) ENGINE=InnoDB COMMENT='Ticket reservation orders';

CREATE TABLE reservation_items (
    reservation_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Reservation line item identifier',
    reservation_id      BIGINT UNSIGNED NOT NULL COMMENT 'Reservation identifier',
    ticket_product_id   BIGINT UNSIGNED NOT NULL COMMENT 'Ticket product identifier',
    product_name        VARCHAR(150) NOT NULL COMMENT 'Product name snapshot at purchase',
    usage_date          DATE NOT NULL COMMENT 'Selected ticket usage date',
    quantity            SMALLINT UNSIGNED NOT NULL COMMENT 'Reserved quantity',
    unit_price          DECIMAL(15,2) NOT NULL COMMENT 'Unit price snapshot at purchase',
    line_amount         DECIMAL(15,2) NOT NULL COMMENT 'Line total amount',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (reservation_item_id),
    UNIQUE KEY uk_reservation_items_reservation_product_date (reservation_id, ticket_product_id, usage_date),
    KEY idx_reservation_items_product_date (ticket_product_id, usage_date),
    CONSTRAINT fk_reservation_items_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_items_product FOREIGN KEY (ticket_product_id) REFERENCES ticket_products (ticket_product_id),
    CONSTRAINT ck_reservation_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservation_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_reservation_items_line_amount CHECK (line_amount = unit_price * quantity)
) ENGINE=InnoDB COMMENT='Ticket products included in a reservation';

CREATE TABLE payments (
    payment_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Payment identifier',
    reservation_id      BIGINT UNSIGNED NOT NULL COMMENT 'Reservation identifier',
    provider            VARCHAR(30) NOT NULL COMMENT 'Payment service provider',
    provider_payment_key VARCHAR(100) NULL COMMENT 'Payment identifier from the provider',
    method              VARCHAR(20) NOT NULL COMMENT 'Payment method (CARD, TRANSFER, VIRTUAL_ACCOUNT, EASY_PAY)',
    status              VARCHAR(20) NOT NULL DEFAULT 'READY' COMMENT 'Payment status (READY, PAID, FAILED, CANCELLED, REFUNDED)',
    amount              DECIMAL(15,2) NOT NULL COMMENT 'Payment amount',
    currency_code       CHAR(3) NOT NULL DEFAULT 'KRW' COMMENT 'ISO 4217 currency code',
    failure_code        VARCHAR(50) NULL COMMENT 'Provider failure code',
    failure_message     VARCHAR(500) NULL COMMENT 'Provider failure message',
    requested_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Payment request time',
    approved_at         DATETIME(6) NULL COMMENT 'Payment approval time',
    cancelled_at        DATETIME(6) NULL COMMENT 'Cancellation or refund time',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payments_provider_key (provider, provider_payment_key),
    KEY idx_payments_reservation_status (reservation_id, status),
    CONSTRAINT fk_payments_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id),
    CONSTRAINT ck_payments_method CHECK (method IN ('CARD', 'TRANSFER', 'VIRTUAL_ACCOUNT', 'EASY_PAY')),
    CONSTRAINT ck_payments_status CHECK (status IN ('READY', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
) ENGINE=InnoDB COMMENT='Reservation payment attempts and results';

CREATE TABLE issued_tickets (
    issued_ticket_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Issued ticket identifier',
    reservation_item_id    BIGINT UNSIGNED NOT NULL COMMENT 'Reservation line item identifier',
    ticket_number          VARCHAR(50) NOT NULL COMMENT 'Public ticket number',
    verification_token_hash CHAR(64) NOT NULL COMMENT 'SHA-256 hash of the ticket verification token',
    issue_method           VARCHAR(20) NOT NULL DEFAULT 'MOBILE' COMMENT 'Issue method (MOBILE, EMAIL, PRINT, ONSITE)',
    status                 VARCHAR(20) NOT NULL DEFAULT 'ISSUED' COMMENT 'Ticket status (ISSUED, USED, CANCELLED, EXPIRED, REPLACED)',
    valid_from             DATETIME(6) NOT NULL COMMENT 'Ticket validity start time',
    valid_until            DATETIME(6) NOT NULL COMMENT 'Ticket validity end time',
    issued_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Issue time',
    used_at                DATETIME(6) NULL COMMENT 'Successful validation time',
    cancelled_at           DATETIME(6) NULL COMMENT 'Cancellation time',
    replaced_by_ticket_id  BIGINT UNSIGNED NULL COMMENT 'Replacement ticket identifier after reissue',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (issued_ticket_id),
    UNIQUE KEY uk_issued_tickets_number (ticket_number),
    UNIQUE KEY uk_issued_tickets_token_hash (verification_token_hash),
    UNIQUE KEY uk_issued_tickets_replaced_by (replaced_by_ticket_id),
    KEY idx_issued_tickets_reservation_item (reservation_item_id),
    KEY idx_issued_tickets_status_valid_until (status, valid_until),
    CONSTRAINT fk_issued_tickets_reservation_item FOREIGN KEY (reservation_item_id) REFERENCES reservation_items (reservation_item_id),
    CONSTRAINT fk_issued_tickets_replacement FOREIGN KEY (replaced_by_ticket_id) REFERENCES issued_tickets (issued_ticket_id) ON DELETE SET NULL,
    CONSTRAINT ck_issued_tickets_method CHECK (issue_method IN ('MOBILE', 'EMAIL', 'PRINT', 'ONSITE')),
    CONSTRAINT ck_issued_tickets_status CHECK (status IN ('ISSUED', 'USED', 'CANCELLED', 'EXPIRED', 'REPLACED')),
    CONSTRAINT ck_issued_tickets_validity CHECK (valid_until > valid_from),
    CONSTRAINT ck_issued_tickets_used_at CHECK (used_at IS NULL OR used_at >= issued_at),
    CONSTRAINT ck_issued_tickets_cancelled_at CHECK (cancelled_at IS NULL OR cancelled_at >= issued_at)
) ENGINE=InnoDB COMMENT='Individually issued mock electronic tickets';

CREATE TABLE ticket_validation_logs (
    ticket_validation_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Ticket validation log identifier',
    issued_ticket_id         BIGINT UNSIGNED NULL COMMENT 'Matched issued ticket identifier; NULL when no ticket matched',
    validator_user_id        BIGINT UNSIGNED NULL COMMENT 'Administrator who performed validation',
    presented_token_fingerprint CHAR(64) NULL COMMENT 'Non-reversible fingerprint of the presented token',
    validation_result       VARCHAR(20) NOT NULL COMMENT 'Validation result (SUCCESS, NOT_FOUND, ALREADY_USED, CANCELLED, EXPIRED)',
    validation_channel      VARCHAR(20) NOT NULL DEFAULT 'ADMIN_WEB' COMMENT 'Validation channel (ADMIN_WEB, MOCK_SCANNER, API)',
    device_id               VARCHAR(100) NULL COMMENT 'Mock scanner or client device identifier',
    failure_reason          VARCHAR(500) NULL COMMENT 'Validation failure explanation',
    metadata                JSON NULL COMMENT 'Additional validation context',
    validated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Validation attempt time',
    PRIMARY KEY (ticket_validation_log_id),
    KEY idx_ticket_validation_logs_ticket_time (issued_ticket_id, validated_at),
    KEY idx_ticket_validation_logs_validator_time (validator_user_id, validated_at),
    KEY idx_ticket_validation_logs_result_time (validation_result, validated_at),
    KEY idx_ticket_validation_logs_fingerprint (presented_token_fingerprint),
    CONSTRAINT fk_ticket_validation_logs_ticket FOREIGN KEY (issued_ticket_id) REFERENCES issued_tickets (issued_ticket_id),
    CONSTRAINT fk_ticket_validation_logs_validator FOREIGN KEY (validator_user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    CONSTRAINT ck_ticket_validation_logs_result CHECK (validation_result IN ('SUCCESS', 'NOT_FOUND', 'ALREADY_USED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_ticket_validation_logs_channel CHECK (validation_channel IN ('ADMIN_WEB', 'MOCK_SCANNER', 'API'))
) ENGINE=InnoDB COMMENT='Mock QR and barcode ticket validation attempts';

-- Seed values used by the planning UI and recommendation model.
INSERT INTO travel_styles (code, name, description, sort_order) VALUES
    ('SIGHTSEEING', '관광', '대표 명소와 문화 공간 중심', 1),
    ('FOOD', '맛집', '지역 음식과 식당 중심', 2),
    ('HEALING', '힐링', '휴식과 여유 중심', 3),
    ('ACTIVITY', '액티비티', '체험과 야외 활동 중심', 4),
    ('CAFE', '카페', '카페와 디저트 중심', 5)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    sort_order = VALUES(sort_order);
