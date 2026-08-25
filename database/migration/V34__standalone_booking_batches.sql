-- 여행을 만들기 전에 확정한 항공·숙소를 보관하고, 이후 완성된 여행에 같은 행을 연결한다.
-- booking_batch_id는 한 번의 항공·숙소 확정을 묶는 클라이언트 생성 UUID다.

ALTER TABLE flight_bookings
    ALTER COLUMN trip_id DROP NOT NULL,
    ADD COLUMN booking_batch_id UUID;

ALTER TABLE accommodation_bookings
    ALTER COLUMN trip_id DROP NOT NULL,
    ADD COLUMN booking_batch_id UUID;

ALTER TABLE flight_bookings
    ADD CONSTRAINT ck_flight_bookings_owner
        CHECK (trip_id IS NOT NULL OR booking_batch_id IS NOT NULL),
    ADD CONSTRAINT uk_flight_bookings_user_batch_leg
        UNIQUE (user_id, booking_batch_id, leg);

ALTER TABLE accommodation_bookings
    ADD CONSTRAINT ck_accommodation_bookings_owner
        CHECK (trip_id IS NOT NULL OR booking_batch_id IS NOT NULL),
    ADD CONSTRAINT uk_accommodation_bookings_user_batch_period
        UNIQUE (user_id, booking_batch_id, check_in, check_out);

CREATE INDEX idx_flight_bookings_unlinked_batch
    ON flight_bookings(user_id, booking_batch_id)
    WHERE trip_id IS NULL;

CREATE INDEX idx_accommodation_bookings_unlinked_batch
    ON accommodation_bookings(user_id, booking_batch_id)
    WHERE trip_id IS NULL;

COMMENT ON COLUMN flight_bookings.booking_batch_id IS
    '여행 생성 전 확정한 항공·숙소를 묶고 이후 여행에 연결하는 UUID';
COMMENT ON COLUMN accommodation_bookings.booking_batch_id IS
    '여행 생성 전 확정한 항공·숙소를 묶고 이후 여행에 연결하는 UUID';
