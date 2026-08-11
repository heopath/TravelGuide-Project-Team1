-- 티켓 모의 예약을 예약 화면의 현재 여행과 연결하고 중복 요청을 막는다.
ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS trip_id BIGINT REFERENCES trips(trip_id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS request_key VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_reservations_trip_created
    ON reservations(trip_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_reservations_user_request_key
    ON reservations(user_id, request_key)
    WHERE request_key IS NOT NULL;

