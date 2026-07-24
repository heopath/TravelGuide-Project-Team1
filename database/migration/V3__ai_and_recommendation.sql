-- All My Trips Flyway migration: V3__ai_and_recommendation
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
-- The database schema must be created before Flyway runs.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

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
