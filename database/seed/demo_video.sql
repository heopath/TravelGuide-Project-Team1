-- 시연 영상 촬영용 데이터.
--
-- 위키의 "기능 구현 영상 시나리오"에 맞춰 화면에 필요한 것을 한 번에 만든다.
-- 찍다 실패하면 아래 되돌리기를 실행하고 이 파일을 다시 돌리면 처음 상태가 된다.
--
--   psql -U allmytrips -d all_my_trips -v email="'쓸계정@example.com'" -f database/seed/demo_video.sql
--
-- 로컬 도커라면:
--   docker compose exec -T postgres psql -U allmytrips -d all_my_trips \
--     -v email="'쓸계정@example.com'" -f - < database/seed/demo_video.sql
--
-- 먼저 local_seed_*.sql을 넣어 장소와 티켓 상품이 있어야 한다.
-- 계정은 화면에서 회원가입으로 먼저 만든다. 비밀번호는 여기서 만들지 않는다.

\set ON_ERROR_STOP on

BEGIN;

-- 어느 계정에 붙일지. -v email로 넘기지 않으면 멈춘다.
\if :{?email}
\else
\echo '계정을 지정해야 한다. 예: -v email="''demo@example.com''"'
\quit 1
\endif

CREATE TEMP TABLE demo_target AS
SELECT user_id FROM users WHERE email = :email;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM demo_target) THEN
        RAISE EXCEPTION '그 이메일의 계정이 없다. 화면에서 회원가입을 먼저 한다.';
    END IF;
END $$;

/* =========================================================
   0. 이전 촬영 데이터 정리

   여러 번 찍어도 같은 상태에서 시작하도록, 이 스크립트가 만든 것만 지운다.
   제목으로 알아본다.
   ========================================================= */

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

/* =========================================================
   1. 관리자 승격

   3장 마지막의 검표와 5장 관리자 운영을 찍으려면 ADMIN이어야 한다.
   ========================================================= */

UPDATE users SET role = 'ADMIN'
 WHERE user_id IN (SELECT user_id FROM demo_target);

/* =========================================================
   2. 다녀온 여행 — 여행 기록과 책 지면(4장)

   기간은 티켓 이용일(2026-09-15)을 감싸도록 잡는다. 그래야 3장에서 예매가
   "여행 기간 밖"으로 막히지 않는다.

   종료일이 오늘보다 앞서야 완료된 여행으로 보여 기록 버튼이 나온다.
   그래서 연도를 지난 해로 두지 않고, 촬영 시점 기준으로 지난 날짜를 쓴다.
   ========================================================= */

WITH created AS (
    INSERT INTO trips (
        user_id, title, destination_name, start_date, end_date,
        companion_type, companion_count, status, source
    )
    SELECT user_id, '[시연] 제주 2박 3일', '제주특별자치도',
           CURRENT_DATE - 20, CURRENT_DATE - 18,
           'FRIENDS', 2, 'CONFIRMED', 'MANUAL'
      FROM demo_target
    RETURNING trip_id, start_date
)
INSERT INTO trip_days (trip_id, day_number, trip_date)
SELECT c.trip_id, g.n, c.start_date + (g.n - 1)
  FROM created c CROSS JOIN generate_series(1, 3) AS g(n);

/* 일정 장소. 지면 아래 동선이 그려지려면 좌표가 있어야 한다.
   같은 DAY 안에서 sort_order가 겹치면 안 된다. */
INSERT INTO itinerary_items (trip_day_id, place_id, item_type, title, sort_order, source)
SELECT d.trip_day_id, p.place_id, 'PLACE', p.name, m.ord, 'MANUAL'
  FROM trip_days d
  JOIN trips t ON t.trip_id = d.trip_id AND t.title = '[시연] 제주 2박 3일'
  JOIN (VALUES
        (1, 35, 1), (1,  3, 2),
        (2, 11, 1), (2, 27, 2),
        (3, 19, 1), (3, 43, 2)
       ) AS m(day_number, place_id, ord) ON m.day_number = d.day_number
  JOIN places p ON p.place_id = m.place_id;

/* 여행 기록과 사진. 사진 주소는 저장을 허용하는 곳이어야 책 지면 내보내기가 된다. */
WITH rec AS (
    INSERT INTO travel_records (trip_id, user_id, title, content, rating, visibility)
    SELECT t.trip_id, t.user_id,
           '비 오는 날의 카페',
           E'첫날은 비가 왔지만 오히려 한적해서 좋았다. 우산을 챙기지 않아 카페에 오래 앉아 있었고, 창밖으로 성산이 흐릿하게 보였다.\n\n둘째 날 일출봉은 사람이 많았다. 마지막 날 공항 가는 길에 들른 카페가 이번 여행의 발견이었다. 서두르지 않아도 되는 하루였다.',
           4, 'PRIVATE'
      FROM trips t WHERE t.title = '[시연] 제주 2박 3일'
    RETURNING travel_record_id
)
INSERT INTO travel_record_images (travel_record_id, image_url, alt_text, sort_order, is_cover)
SELECT r.travel_record_id, i.url, i.alt, i.ord, i.cover
  FROM rec r CROSS JOIN (VALUES
        ('https://picsum.photos/seed/amt-demo-1/1600/1100', '성산 일출봉',   1, TRUE),
        ('https://picsum.photos/seed/amt-demo-2/1200/1600', '비 오는 카페',   2, FALSE),
        ('https://picsum.photos/seed/amt-demo-3/1600/1000', '우도 가는 배',   3, FALSE)
       ) AS i(url, alt, ord, cover);

/* =========================================================
   3. 앞으로 갈 여행 — 대비용(4장)

   완료된 여행에만 기록 버튼이 나온다는 것을 보여주려면 나란히 둘 게 필요하다.
   ========================================================= */

WITH created AS (
    INSERT INTO trips (
        user_id, title, destination_name, start_date, end_date,
        companion_type, companion_count, status, source
    )
    SELECT user_id, '[시연] 다음 달 부산', '부산광역시',
           CURRENT_DATE + 30, CURRENT_DATE + 32,
           'FAMILY', 3, 'CONFIRMED', 'MANUAL'
      FROM demo_target
    RETURNING trip_id, start_date
)
INSERT INTO trip_days (trip_id, day_number, trip_date)
SELECT c.trip_id, g.n, c.start_date + (g.n - 1)
  FROM created c CROSS JOIN generate_series(1, 3) AS g(n);

/* =========================================================
   4. 지금 예매할 여행 — 예약·결제·검표(3장)

   티켓 이용일(2026-09-15)을 기간 안에 넣는다. 이 여행으로 예약해야 3장이
   막히지 않고 이어진다.
   ========================================================= */

WITH created AS (
    INSERT INTO trips (
        user_id, title, destination_name, start_date, end_date,
        companion_type, companion_count, status, source
    )
    SELECT user_id, '[시연] 예매용 여행', '서울특별시',
           DATE '2026-09-14', DATE '2026-09-16',
           'SOLO', 1, 'CONFIRMED', 'MANUAL'
      FROM demo_target
    RETURNING trip_id, start_date
)
INSERT INTO trip_days (trip_id, day_number, trip_date)
SELECT c.trip_id, g.n, c.start_date + (g.n - 1)
  FROM created c CROSS JOIN generate_series(1, 3) AS g(n);

/* =========================================================
   5. 추천장소 노출

   빈 DB에 시드를 넣으면 V23 백필이 켤 대상이 없어 추천장소 화면이 빈다.
   2장에서 장소를 담아야 하므로 켜 둔다.
   ========================================================= */

UPDATE places SET is_recommended = TRUE
 WHERE external_provider = 'LOCAL_SEED' AND is_active = TRUE AND is_recommended = FALSE;

COMMIT;

/* 확인 */
SELECT t.title, t.status, t.start_date, t.end_date,
       (SELECT count(*) FROM trip_days d WHERE d.trip_id = t.trip_id)      AS 일수,
       (SELECT count(*) FROM itinerary_items i
          JOIN trip_days d ON d.trip_day_id = i.trip_day_id
         WHERE d.trip_id = t.trip_id)                                      AS 담은장소,
       (SELECT count(*) FROM travel_records r WHERE r.trip_id = t.trip_id) AS 기록
  FROM trips t
 WHERE t.title LIKE '[시연]%'
 ORDER BY t.trip_id;
