BEGIN;

WITH source AS (
    SELECT i,
           CASE WHEN i <= 25 THEN 'ATTRACTION'
                WHEN i <= 45 THEN 'RESTAURANT'
                WHEN i <= 60 THEN 'CAFE'
                WHEN i <= 75 THEN 'ACCOMMODATION'
                WHEN i <= 80 THEN 'FESTIVAL'
                WHEN i <= 90 THEN 'ACTIVITY'
                ELSE 'TRANSPORT' END AS category,
           (ARRAY['서울','부산','제주','강릉','경주','전주','여수','인천'])[((i - 1) % 8) + 1] AS region_name
    FROM generate_series(1, 100) AS g(i)
)
INSERT INTO places (place_id, external_provider, external_place_id, category, name, country_code,
                    region, city, address, latitude, longitude, description, phone, website_url,
                    average_rating, is_active)
SELECT i, 'LOCAL_SEED', 'PLACE-' || lpad(i::text, 3, '0'), category,
       region_name || ' ' || category || ' ' || lpad(i::text, 3, '0'), 'KR', region_name,
       region_name || '시', region_name || ' 테스트로 ' || i,
       33.2 + (i % 40) * 0.12, 126.1 + (i % 50) * 0.11,
       region_name || ' 합성 장소', '02-0000-0000',
       'https://example.invalid/' || CASE WHEN category = 'ACCOMMODATION' THEN 'accommodations/' ELSE 'places/' END || i,
       3.2 + (i % 18) * 0.1, TRUE
FROM source;

INSERT INTO place_images (place_id, image_url, alt_text, sort_order, is_primary)
SELECT i, 'https://picsum.photos/seed/all-my-trips-place-' || i || '-' || image_no || '/1200/800',
       '합성 장소 이미지', image_no, image_no = 1
FROM generate_series(1, 100) AS p(i)
CROSS JOIN generate_series(1, 2) AS img(image_no);

WITH place_category AS (
    SELECT place_id, category FROM places WHERE external_provider = 'LOCAL_SEED'
), style_pairs AS (
    SELECT place_id,
           CASE category WHEN 'RESTAURANT' THEN 2 WHEN 'CAFE' THEN 5 WHEN 'ACCOMMODATION' THEN 1 WHEN 'ACTIVITY' THEN 4 ELSE 1 END AS style1,
           CASE category WHEN 'RESTAURANT' THEN 5 WHEN 'CAFE' THEN 3 WHEN 'FESTIVAL' THEN 4 ELSE 3 END AS style2
    FROM place_category
)
INSERT INTO place_travel_styles (place_id, travel_style_id, relevance_score, source)
SELECT place_id, style_id, score, 'MANUAL'
FROM style_pairs
CROSS JOIN LATERAL (VALUES (style1, 88 - place_id % 7), (style2, 70 - place_id % 7)) AS s(style_id, score);

INSERT INTO favorites (user_id, place_id, memo)
SELECT ((i - 1) % 8) + 1, ((i * 7 - 1) % 100) + 1,
       CASE WHEN i % 3 = 0 THEN '후보 메모 ' || i END
FROM generate_series(1, 40) AS g(i);

INSERT INTO travel_themes (travel_theme_id, title, description, country_code, representative_region,
                           duration_days, min_budget, max_budget, currency_code, image_url, status, created_by)
VALUES
(1, '이번 주말 부산 미식 여행', '부산의 대표 먹거리와 해변을 함께 즐기는 테마', 'KR', '부산', 3, 250000, 500000, 'KRW', 'https://picsum.photos/seed/theme-busan/1200/800', 'PUBLISHED', 9),
(2, '제주의 조용한 오름과 카페', '오름과 카페 중심의 여유로운 제주 여행', 'KR', '제주', 4, 350000, 700000, 'KRW', 'https://picsum.photos/seed/theme-jeju/1200/800', 'PUBLISHED', 9),
(3, '서울 야경과 전시 산책', '전시와 야경 명소를 연결한 도심 여행', 'KR', '서울', 2, 150000, 350000, 'KRW', 'https://picsum.photos/seed/theme-seoul/1200/800', 'PUBLISHED', 10),
(4, '아이와 함께하는 경주', '가족이 함께 둘러보는 경주 역사 여행', 'KR', '경주', 3, 300000, 600000, 'KRW', 'https://picsum.photos/seed/theme-gyeongju/1200/800', 'PUBLISHED', 10);

INSERT INTO travel_theme_styles (travel_theme_id, travel_style_id)
SELECT theme_id, style_id
FROM generate_series(1, 4) AS t(theme_id)
CROSS JOIN LATERAL (VALUES (((theme_id - 1) % 5) + 1), ((theme_id % 5) + 1)) AS s(style_id);

INSERT INTO travel_theme_places (travel_theme_id, place_id, day_number, sort_order, recommendation_note)
SELECT theme_id, ((theme_id - 1) * 8 + n), ((n - 1) / 3) + 1, n, '합성 테마 추천 장소'
FROM generate_series(1, 4) AS t(theme_id)
CROSS JOIN generate_series(1, 6) AS p(n);

SELECT setval(pg_get_serial_sequence('places', 'place_id'), 100, true);
SELECT setval(pg_get_serial_sequence('travel_themes', 'travel_theme_id'), 4, true);
COMMIT;
