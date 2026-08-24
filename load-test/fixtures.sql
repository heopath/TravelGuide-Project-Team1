-- 티켓 예약 대기열 부하 테스트용 데이터
--
-- ⚠️ 로컬 DB에서만 실행합니다. 운영 RDS에 넣으면 가짜 계정과 예약이 그대로 남아
--    운영 지표(오늘 예약)와 예약 모니터링 숫자가 오염됩니다.
--
-- 실행 전에 접속한 DB를 확인하세요.
--   SELECT current_database(), inet_server_addr();
--
-- 만드는 것
--   - 부하용 계정 30개            loadtest1@example.com ~ loadtest30@example.com
--   - 계정마다 여행 1개            부하테스트 여행 N
--   - 전용 장소·상품·옵션·시간대   재고 10개
--
-- 지우는 것은 파일 맨 아래 정리 구문에 있습니다.

-- ── 0. 비밀번호 준비 ────────────────────────────────────────────────────────
--
-- 비밀번호는 BCrypt로 저장됩니다(SecurityConfig의 BCryptPasswordEncoder).
-- 해시를 직접 만들지 않고, 회원가입으로 만든 계정의 해시를 복사해 씁니다.
--
--   1) 화면에서 loadtest1@example.com 으로 회원가입합니다.
--      비밀번호는 부하 테스트용으로만 쓸 값을 새로 정합니다.
--   2) 아래 구문을 실행하면 나머지 29개가 같은 비밀번호로 만들어집니다.
--
-- 본인 계정의 해시를 복사하지 마세요. 그 비밀번호를 k6 스크립트에 적게 됩니다.
--
-- 정한 비밀번호를 이 파일이나 문서에 적지 마세요. 이 저장소는 공개(public)라
-- 한 번 커밋되면 이력에 남아 되돌리기 어렵습니다. k6에는 실행할 때
-- -e PASSWORD= 로 넘깁니다.

-- ── 1. 계정 (2 ~ :accounts) ─────────────────────────────────────────────────
--
-- 기본 30개입니다. 더 큰 VU로 돌리려면 psql에 -v accounts=60 처럼 넘깁니다.
-- k6의 VUS는 이 수를 넘기면 안 됩니다. VU 번호로 계정을 고르므로, 계정보다 VU가 많으면
-- 없는 계정으로 로그인해 회차 전체가 실패합니다.
--
--   psql -v accounts=60 -f load-test/fixtures.sql
--   k6 run -e VUS=60 -e SLOT_ID=<slot_id> load-test/booking-queue.js
--
-- 재고도 함께 올려야 의미가 있습니다. 아래 3번의 total_quantity를 -v stock= 으로 넘깁니다.
\if :{?accounts}
\else
\set accounts 30
\endif

INSERT INTO users (email, password_hash, nickname, role, status)
SELECT
    'loadtest' || i || '@example.com',
    (SELECT password_hash FROM users WHERE email = 'loadtest1@example.com'),
    'loadtest' || i,
    'USER',
    'ACTIVE'
FROM generate_series(2, :accounts) AS i
ON CONFLICT (email) DO NOTHING;

-- 확인: :accounts 개가 나와야 합니다.
-- SELECT count(*) FROM users WHERE email LIKE 'loadtest%@example.com';

-- ── 2. 계정마다 여행 1개 ────────────────────────────────────────────────────
--
-- 여행이 계정마다 필요합니다. 예약은 여행 소유자만 할 수 있고,
-- 한 여행으로 여러 번 부르면 requestKey가 달라도 같은 사람이라 경쟁이 약해집니다.
--
-- 기간을 넉넉히 잡습니다. 티켓 이용일이 여행 기간 밖이면 예약이 거부됩니다.
INSERT INTO trips (user_id, title, destination_name, start_date, end_date,
                   companion_type, companion_count, status)
SELECT
    u.user_id,
    '부하테스트 여행 ' || u.nickname,
    '부하테스트',
    CURRENT_DATE,
    CURRENT_DATE + 30,
    'SOLO',
    1,
    'DRAFT'
FROM users u
WHERE u.email LIKE 'loadtest%@example.com'
  AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.user_id = u.user_id AND t.destination_name = '부하테스트'
  );

-- ── 3. 전용 장소·상품·옵션·시간대 ───────────────────────────────────────────
--
-- 시드 데이터를 쓰지 않고 따로 만듭니다. 시드 상품에 부하를 걸면 재고가 실제로 깎여
-- 다음 사람이 화면을 확인할 때 품절로 보입니다.
--
-- 재고 기본값은 10입니다. VU를 늘릴 때 함께 올립니다: -v stock=30
-- 재고가 VU 수와 같아지면 아무도 못 사는 사람이 없어 "재고 소진" 경로를 확인하지 못합니다.
-- 경쟁을 보려면 재고를 VU보다 적게 둡니다.
\if :{?stock}
\else
\set stock 10
\endif
WITH new_place AS (
    INSERT INTO places (category, name, country_code, region, city, is_active)
    VALUES ('ACTIVITY', '부하테스트 체험장', 'KR', '부하테스트', '부하테스트', TRUE)
    RETURNING place_id
), new_product AS (
    INSERT INTO ticket_products
        (place_id, name, description, sale_start_at, sale_end_at,
         usage_start_date, usage_end_date, status)
    SELECT
        place_id, '부하테스트 입장권', '대기열 부하 테스트 전용입니다.',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '60 day',
        CURRENT_DATE, CURRENT_DATE + 60, 'ON_SALE'
    FROM new_place
    RETURNING ticket_product_id
), new_option AS (
    INSERT INTO ticket_product_options
        (ticket_product_id, name, unit_price, currency_code,
         max_quantity_per_user, sort_order, is_active)
    SELECT ticket_product_id, '성인', 10000, 'KRW', 10, 1, TRUE
    FROM new_product
    RETURNING ticket_product_option_id
), new_slot AS (
    INSERT INTO ticket_time_slots (ticket_product_option_id, usage_date, start_time, end_time, status)
    SELECT ticket_product_option_id, CURRENT_DATE + 1, '10:00', '11:00', 'OPEN'
    FROM new_option
    RETURNING ticket_time_slot_id
)
INSERT INTO ticket_inventory (ticket_time_slot_id, total_quantity, reserved_quantity)
SELECT ticket_time_slot_id, :stock, 0 FROM new_slot;

-- ── 4. k6에 넘길 값 확인 ────────────────────────────────────────────────────
--
-- 아래 결과의 slot_id를 SLOT_ID로 씁니다.
--   k6 run -e VUS=30 -e SLOT_ID=<slot_id> load-test/booking-queue.js
--
-- k6 스크립트는 VU 번호로 계정을 고르므로(loadtest1 ~ loadtestN) 계정은 따로 넘기지 않습니다.
--
-- 여행은 넘기지 않습니다. 예약은 여행 소유자만 할 수 있어서 모든 VU가 같은 여행을 쓰면
-- 소유자 한 명만 성공하고 나머지는 전부 거부됩니다. 각 VU가 로그인 후 자기 여행을 찾습니다.
-- 아래 trip_id는 VU 하나로 디버깅할 때 -e TRIP_ID= 로 넘기는 용도입니다.
SELECT
    s.ticket_time_slot_id AS slot_id,
    i.total_quantity      AS stock,
    i.reserved_quantity   AS reserved,
    (SELECT MIN(t.trip_id) FROM trips t
      JOIN users u ON u.user_id = t.user_id
     WHERE u.email LIKE 'loadtest%@example.com') AS trip_id,
    (SELECT COUNT(*) FROM users WHERE email LIKE 'loadtest%@example.com') AS accounts
FROM ticket_time_slots s
JOIN ticket_product_options o ON o.ticket_product_option_id = s.ticket_product_option_id
JOIN ticket_products p        ON p.ticket_product_id = o.ticket_product_id
JOIN ticket_inventory i       ON i.ticket_time_slot_id = s.ticket_time_slot_id
WHERE p.name = '부하테스트 입장권';

-- ── 5. 회차 사이 초기화 ─────────────────────────────────────────────────────
--
-- 부하를 한 번 돌리면 재고가 소진됩니다. 다시 돌리기 전에 초기화하세요.
--
-- ⚠️ 아래 구문을 복사해 쓰지 말고 reset.sql을 실행하세요. 주석이라 실행되지 않고
--    여기까지 스크롤해야 보여서, 실제로 회차 사이에 돌리지 않는 일이 있었습니다.
--    그러면 reserved_quantity가 남은 채로 다음 회차가 돌아 측정값이 망가집니다.
--
--    docker compose exec -T postgres psql -U allmytrips -d all_my_trips -f - < load-test/reset.sql
--
-- 아래는 reset.sql이 하는 일을 그대로 옮긴 것입니다(참고용).
--
-- DELETE FROM reservation_items
--  WHERE reservation_id IN (
--      SELECT r.reservation_id FROM reservations r
--       JOIN users u ON u.user_id = r.user_id
--       WHERE u.email LIKE 'loadtest%@example.com');
--
-- DELETE FROM reservations
--  WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE 'loadtest%@example.com');
--
-- UPDATE ticket_inventory i
--    SET reserved_quantity = 0, version = version + 1
--   FROM ticket_time_slots s
--   JOIN ticket_product_options o ON o.ticket_product_option_id = s.ticket_product_option_id
--   JOIN ticket_products p        ON p.ticket_product_id = o.ticket_product_id
--  WHERE i.ticket_time_slot_id = s.ticket_time_slot_id
--    AND p.name = '부하테스트 입장권';
--
-- Redis에 남은 대기열도 함께 비웁니다. 안 비우면 이전 회차의 줄이 이어집니다.
--   redis-cli --scan --pattern 'all-my-trips:booking-queue:*' | xargs -r redis-cli del
--
-- Windows에서는 xargs가 없으니 이렇게 씁니다.
--   redis-cli KEYS "all-my-trips:booking-queue:*" | ForEach-Object { redis-cli DEL $_ }

-- ── 6. 전부 정리 ────────────────────────────────────────────────────────────
--
-- 순서를 지켜야 외래키에 걸리지 않습니다.
--
-- DELETE FROM reservation_items
--  WHERE reservation_id IN (
--      SELECT r.reservation_id FROM reservations r
--       JOIN users u ON u.user_id = r.user_id
--       WHERE u.email LIKE 'loadtest%@example.com');
--
-- DELETE FROM reservations
--  WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE 'loadtest%@example.com');
--
-- DELETE FROM ticket_inventory
--  WHERE ticket_time_slot_id IN (
--      SELECT s.ticket_time_slot_id FROM ticket_time_slots s
--       JOIN ticket_product_options o ON o.ticket_product_option_id = s.ticket_product_option_id
--       JOIN ticket_products p        ON p.ticket_product_id = o.ticket_product_id
--       WHERE p.name = '부하테스트 입장권');
--
-- DELETE FROM ticket_time_slots
--  WHERE ticket_product_option_id IN (
--      SELECT o.ticket_product_option_id FROM ticket_product_options o
--       JOIN ticket_products p ON p.ticket_product_id = o.ticket_product_id
--       WHERE p.name = '부하테스트 입장권');
--
-- DELETE FROM ticket_product_options
--  WHERE ticket_product_id IN (SELECT ticket_product_id FROM ticket_products WHERE name = '부하테스트 입장권');
--
-- DELETE FROM ticket_products WHERE name = '부하테스트 입장권';
-- DELETE FROM places WHERE name = '부하테스트 체험장';
--
-- DELETE FROM trips
--  WHERE user_id IN (SELECT user_id FROM users WHERE email LIKE 'loadtest%@example.com');
--
-- DELETE FROM users WHERE email LIKE 'loadtest%@example.com';
