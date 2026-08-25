-- 선택한 항공편의 공항 정보를 예약 스냅샷에 보존한다.
-- 기존 예약은 공항 정보가 없을 수 있으므로 nullable로 추가하고,
-- 이후 새로 저장되는 항공편부터 매칭에 사용한다.
ALTER TABLE flight_bookings
    ADD COLUMN IF NOT EXISTS origin VARCHAR(3),
    ADD COLUMN IF NOT EXISTS destination VARCHAR(3);

COMMENT ON COLUMN flight_bookings.origin IS '출발 공항 IATA 코드';
COMMENT ON COLUMN flight_bookings.destination IS '도착 공항 IATA 코드';
