BEGIN;

WITH trip_source AS (
    SELECT tid,
           CASE WHEN tid <= 10 THEN 3 ELSE 2 END AS duration_days,
           DATE '2026-08-01' + ((tid - 1) * 5) AS start_day,
           (ARRAY['서울','부산','제주','강릉','경주','전주','여수','인천'])[((tid - 1) % 8) + 1] AS region_name
    FROM generate_series(1, 15) AS g(tid)
)
INSERT INTO trips (trip_id, user_id, title, destination_name, start_date, end_date, companion_type,
                   companion_count, purpose, budget_amount, currency_code, transport_preference,
                   food_preference, pace, accommodation_style, status, source)
SELECT tid, ((tid - 1) % 8) + 1, region_name || ' 여행 ' || tid, region_name, start_day,
       start_day + (duration_days - 1),
       (ARRAY['SOLO','FRIENDS','COUPLE','FAMILY','GROUP'])[((tid - 1) % 5) + 1],
       (tid % 4) + 1, '지역 여행', 300000 + tid * 50000, 'KRW', '대중교통', '지역 음식',
       (ARRAY['RELAXED','NORMAL','PACKED'])[(tid % 3) + 1], '외부 예약 숙소',
       CASE WHEN tid <= 4 THEN 'DRAFT' WHEN tid <= 10 THEN 'CONFIRMED' WHEN tid <= 14 THEN 'COMPLETED' ELSE 'CANCELLED' END,
       CASE WHEN tid % 2 = 0 THEN 'AI' ELSE 'MANUAL' END
FROM trip_source;

INSERT INTO trip_travel_styles (trip_id, travel_style_id, priority)
SELECT tid, style_id, priority
FROM generate_series(1, 15) AS t(tid)
CROSS JOIN LATERAL (VALUES (((tid - 1) % 5) + 1, 1), ((tid % 5) + 1, 2)) AS s(style_id, priority);

WITH days AS (
    SELECT tid, d,
           CASE WHEN tid <= 10 THEN (tid - 1) * 3 + d ELSE 30 + (tid - 11) * 2 + d END AS day_id,
           DATE '2026-08-01' + ((tid - 1) * 5) AS start_day,
           (ARRAY['서울','부산','제주','강릉','경주','전주','여수','인천'])[((tid - 1) % 8) + 1] AS region_name
    FROM generate_series(1, 15) AS t(tid)
    CROSS JOIN LATERAL generate_series(1, CASE WHEN tid <= 10 THEN 3 ELSE 2 END) AS x(d)
)
INSERT INTO trip_days (trip_day_id, trip_id, day_number, trip_date, title, memo)
SELECT day_id, tid, d, start_day + (d - 1), region_name || ' ' || d || '일차', '합성 일정'
FROM days;

WITH day_source AS (
    SELECT trip_day_id, trip_id, day_number,
           (ARRAY['서울','부산','제주','강릉','경주','전주','여수','인천'])[((trip_id - 1) % 8) + 1] AS region_name
    FROM trip_days
)
INSERT INTO itinerary_items (itinerary_item_id, trip_day_id, place_id, item_type, title, start_time,
                             end_time, sort_order, memo, estimated_cost, currency_code, source)
SELECT (trip_day_id - 1) * 4 + s, trip_day_id,
       ((trip_id - 1) % 8) + 1 + 8 * ((day_number * 4 + s) % 12),
       (ARRAY['PLACE','MEAL','ACTIVITY','PLACE'])[s], region_name || ' 일정 ' || day_number || '-' || s,
       make_time(7 + s * 2, 0, 0), make_time(8 + s * 2, 30, 0), s, '합성 일정 항목',
       10000 + s * 8000, 'KRW', CASE WHEN trip_id % 2 = 0 THEN 'AI' ELSE 'MANUAL' END
FROM day_source CROSS JOIN generate_series(1, 4) AS g(s);

SELECT setval(pg_get_serial_sequence('trips', 'trip_id'), 15, true);
SELECT setval(pg_get_serial_sequence('trip_days', 'trip_day_id'), 40, true);
SELECT setval(pg_get_serial_sequence('itinerary_items', 'itinerary_item_id'), 160, true);
COMMIT;
