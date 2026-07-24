-- All My Trips Flyway migration: V2__trip_and_itinerary
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
-- The database schema must be created before Flyway runs.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

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
