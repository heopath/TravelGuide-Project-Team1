-- All My Trips Flyway migration: V7__ticket_reservation
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
-- The database schema must be created before Flyway runs.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

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
