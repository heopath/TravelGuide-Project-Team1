-- 티켓 예약을 여행에서 떼어낸다. (#255)
--
-- trip_id는 V16에서 덧붙인 컬럼이고 처음부터 NULL을 허용했으므로 컬럼 자체는 그대로 둔다.
-- 바꾸는 것은 삭제 규칙 하나다.
--
-- ON DELETE CASCADE는 여행을 지우면 그 여행에 딸린 예약·결제·발급 티켓까지 함께 지운다.
-- 티켓이 여행과 독립이 되면 이건 사고다. 손님이 여행 계획을 지웠다는 이유로 돈 주고 산
-- 티켓이 사라지고, 환불 기록도 남지 않는다.
--
-- SET NULL이면 여행만 사라지고 예약은 "여행에 붙지 않은 티켓"으로 남는다.

ALTER TABLE reservations
    DROP CONSTRAINT IF EXISTS reservations_trip_id_fkey;

ALTER TABLE reservations
    ADD CONSTRAINT reservations_trip_id_fkey
        FOREIGN KEY (trip_id) REFERENCES trips(trip_id) ON DELETE SET NULL;

-- 여행 없는 예약을 사용자 기준으로 훑는 길. 마이페이지 "내 티켓"(#253)이 쓴다.
-- idx_reservations_user_status_created는 status를 먼저 타서 상태를 안 거를 때 덜 맞는다.
CREATE INDEX IF NOT EXISTS idx_reservations_user_created
    ON reservations(user_id, created_at DESC);
