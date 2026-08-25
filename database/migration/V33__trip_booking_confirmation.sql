-- 외부 결제를 검증하지 않는 실습 예약 흐름의 최종 확정 시각이다.
-- 여행 작성 상태(trips.status)와 분리해 일정 저장 여부와 예약 확정 여부가 섞이지 않게 한다.
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS booking_confirmed_at TIMESTAMPTZ;

COMMENT ON COLUMN trips.booking_confirmed_at IS
    '항공·숙소·티켓 선택을 사용자가 실습 예약으로 최종 확정한 시각';
