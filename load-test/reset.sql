-- 부하 테스트 회차 사이 초기화
--
-- ⚠️ 로컬 DB에서만 실행합니다. 실행 전에 접속한 DB를 확인하세요.
--      SELECT current_database(), inet_server_addr();
--
-- 부하를 한 번 돌리면 재고가 소진됩니다. 다시 돌리기 전에 이 파일을 실행하세요.
--
--   docker compose exec -T postgres psql -U allmytrips -d all_my_trips -f - < load-test/reset.sql
--
-- fixtures.sql이 만든 계정·상품·시간대는 그대로 두고, 회차마다 쌓이는 것만 지웁니다.
-- 전부 지우려면 fixtures.sql 맨 아래 "6. 전부 정리"를 쓰세요.
--
-- 이 구문은 원래 fixtures.sql 안에 주석으로만 있었습니다. 주석이라 실행되지 않았고
-- 150줄 아래 묻혀 있어 회차 사이에 아무도 돌리지 않았습니다. 그 결과 reserved_quantity가
-- 남은 채로 다음 회차가 돌아, 재고 10개짜리에서 4개만 성공하는 식으로 측정값이 망가졌습니다.
-- 그래서 실행 가능한 파일로 분리했습니다.

BEGIN;

-- 1. 부하 계정이 만든 예약 (자식 먼저)
DELETE FROM reservation_items
 WHERE reservation_id IN (
     SELECT r.reservation_id FROM reservations r
      JOIN users u ON u.user_id = r.user_id
      WHERE u.email LIKE 'loadtest%@example.com');

DELETE FROM reservations
 WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE 'loadtest%@example.com');

-- 2. 잡아둔 재고 되돌리기
--
-- 예약을 지워도 이 값은 따라 내려가지 않습니다. 여기를 빼먹으면 다음 회차의 가용 재고가
-- 줄어든 채로 시작해, 대기열이 아니라 재고 때문에 거절되는 것을 대기열 문제로 오해하게 됩니다.
-- version은 낙관적 잠금 컬럼이라 함께 올립니다.
UPDATE ticket_inventory i
   SET reserved_quantity = 0, version = version + 1
  FROM ticket_time_slots s
  JOIN ticket_product_options o ON o.ticket_product_option_id = s.ticket_product_option_id
  JOIN ticket_products p        ON p.ticket_product_id = o.ticket_product_id
 WHERE i.ticket_time_slot_id = s.ticket_time_slot_id
   AND p.name = '부하테스트 입장권';

COMMIT;

-- 3. 확인 — 예약됨이 0이어야 합니다
SELECT s.ticket_time_slot_id AS slot_id,
       i.total_quantity      AS 재고,
       i.reserved_quantity   AS 예약됨
  FROM ticket_time_slots s
  JOIN ticket_inventory i       ON i.ticket_time_slot_id = s.ticket_time_slot_id
  JOIN ticket_product_options o ON o.ticket_product_option_id = s.ticket_product_option_id
  JOIN ticket_products p        ON p.ticket_product_id = o.ticket_product_id
 WHERE p.name = '부하테스트 입장권'
 ORDER BY 1;

-- 4. Redis 대기열도 비웁니다
--
-- DB만 되돌리면 이전 회차의 줄이 Redis에 남아 다음 회차와 이어집니다.
--
--   docker compose exec -T redis redis-cli --scan --pattern 'all-my-trips:booking-queue:*' | xargs -r redis-cli del
--
-- Windows PowerShell에는 xargs가 없습니다.
--   docker compose exec -T redis redis-cli --scan --pattern 'all-my-trips:booking-queue:*' | ForEach-Object { docker compose exec -T redis redis-cli DEL $_ }
