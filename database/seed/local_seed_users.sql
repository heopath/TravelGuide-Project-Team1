-- PostgreSQL synthetic local data only. Replace the password placeholder before login testing.
BEGIN;

INSERT INTO travel_styles (travel_style_id, code, name, description, sort_order) VALUES
(1, 'SIGHTSEEING', '관광', '대표 명소 중심', 1),
(2, 'FOOD', '맛집', '지역 음식 중심', 2),
(3, 'HEALING', '힐링', '휴식 중심', 3),
(4, 'ACTIVITY', '액티비티', '체험 중심', 4),
(5, 'CAFE', '카페', '카페 중심', 5);

INSERT INTO users (user_id, email, password_hash, nickname, role, status)
SELECT i,
       'local-user' || i || '@example.invalid',
       'REPLACE_WITH_TEAM_BCRYPT_HASH',
       '테스트사용자' || i,
       CASE WHEN i >= 9 THEN 'ADMIN' ELSE 'USER' END,
       CASE WHEN i = 7 THEN 'SUSPENDED' WHEN i = 8 THEN 'WITHDRAWN' ELSE 'ACTIVE' END
FROM generate_series(1, 10) AS g(i);

INSERT INTO user_preferences (user_id, travel_style_id, preference_score, source)
SELECT i,
       ((i + j - 1) % 5) + 1,
       55 + ((i * 7 + j * 11) % 46),
       CASE WHEN j = 2 THEN 'INFERRED' ELSE 'EXPLICIT' END
FROM generate_series(1, 10) AS u(i)
CROSS JOIN generate_series(0, 2) AS p(j);

SELECT setval(pg_get_serial_sequence('travel_styles', 'travel_style_id'), 5, true);
SELECT setval(pg_get_serial_sequence('users', 'user_id'), 10, true);
COMMIT;
