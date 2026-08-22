-- 시연 영상 촬영 데이터 되돌리기.
--
-- demo_video.sql이 만든 것만 지운다. 제목이 "[시연]"으로 시작하는 여행과
-- 거기 딸린 것들이다. 원래 있던 데이터는 건드리지 않는다.
--
--   docker compose exec -T postgres psql -U allmytrips -d all_my_trips -f - < database/seed/demo_video_reset.sql
--
-- 촬영 중 예약·결제까지 진행했다면 그 예약도 함께 지운다. 아래 2번을 보라.

\set ON_ERROR_STOP on

BEGIN;

/* 1. 여행과 거기 딸린 것 */

DELETE FROM travel_record_images
 WHERE travel_record_id IN (
     SELECT r.travel_record_id FROM travel_records r
      JOIN trips t ON t.trip_id = r.trip_id
     WHERE t.title LIKE '[시연]%'
 );

DELETE FROM travel_records
 WHERE trip_id IN (SELECT trip_id FROM trips WHERE title LIKE '[시연]%');

DELETE FROM itinerary_items
 WHERE trip_day_id IN (
     SELECT d.trip_day_id FROM trip_days d
      JOIN trips t ON t.trip_id = d.trip_id
     WHERE t.title LIKE '[시연]%'
 );

DELETE FROM trip_days
 WHERE trip_id IN (SELECT trip_id FROM trips WHERE title LIKE '[시연]%');

DELETE FROM trips WHERE title LIKE '[시연]%';

/*
 * 2. 촬영 중 만든 예약
 *
 * 기본으로는 지우지 않는다. 예약은 결제·티켓·검표 기록까지 이어져 있어서
 * 잘못 지우면 다른 데이터까지 흔들린다. 다시 찍느라 예약이 쌓였다면 아래
 * 주석을 풀고 계정을 지정해 지운다.
 *
 * DELETE FROM issued_tickets WHERE reservation_item_id IN (
 *     SELECT ri.reservation_item_id FROM reservation_items ri
 *      JOIN reservations r ON r.reservation_id = ri.reservation_id
 *      JOIN users u ON u.user_id = r.user_id
 *     WHERE u.email = '쓴계정@example.com'
 * );
 * DELETE FROM reservation_items WHERE reservation_id IN (
 *     SELECT r.reservation_id FROM reservations r
 *      JOIN users u ON u.user_id = r.user_id
 *     WHERE u.email = '쓴계정@example.com'
 * );
 * DELETE FROM reservations WHERE user_id IN (
 *     SELECT user_id FROM users WHERE email = '쓴계정@example.com'
 * );
 */

COMMIT;

/* 확인 — 0건이어야 한다 */
SELECT count(*) AS 남은시연여행 FROM trips WHERE title LIKE '[시연]%';
