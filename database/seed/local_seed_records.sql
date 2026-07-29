-- Quantity conflict: 4 COMPLETED trips permit 4 records under the one-record-per-trip rule.
BEGIN;

INSERT INTO travel_records (travel_record_id, trip_id, user_id, title, content, rating, visibility)
SELECT r, r + 10, ((r + 9) % 8) + 1, '완료 여행 기록 ' || r, '완료 여행 합성 후기',
       CASE WHEN r % 2 = 0 THEN 5 ELSE 4 END,
       CASE WHEN r % 2 = 1 THEN 'PUBLIC' ELSE 'PRIVATE' END
FROM generate_series(1, 4) AS g(r);

INSERT INTO travel_record_images (travel_record_id, image_url, alt_text, sort_order, is_cover)
SELECT r, 'https://picsum.photos/seed/all-my-trips-record-' || r || '-' || image_no || '/1200/800',
       CASE WHEN image_no = 1 THEN '대표 이미지' ELSE '추가 이미지' END,
       image_no, image_no = 1
FROM generate_series(1, 4) AS rec(r)
CROSS JOIN generate_series(1, 4) AS img(image_no);

INSERT INTO travel_record_comments (
    travel_record_comment_id, travel_record_id, user_id, parent_comment_id, content)
SELECT (r - 1) * 6 + j, r, ((r + j) % 8) + 1,
       CASE WHEN j = 5 THEN (r - 1) * 6 + 1 WHEN j = 6 THEN (r - 1) * 6 + 2 END,
       CASE WHEN j <= 4 THEN '기록 ' || r || ' 댓글 ' || j ELSE '합성 답글' END
FROM generate_series(1, 4) AS rec(r)
CROSS JOIN generate_series(1, 6) AS c(j);

INSERT INTO travel_record_likes (travel_record_id, user_id)
SELECT r, u FROM generate_series(1, 4) AS rec(r) CROSS JOIN generate_series(1, 8) AS usr(u);

INSERT INTO travel_record_shares (travel_record_id, user_id, channel, shared_at)
SELECT ((i - 1) % 4) + 1, CASE WHEN i % 4 = 0 THEN NULL ELSE ((i - 1) % 8) + 1 END,
       (ARRAY['LINK','LINK','KAKAO','OTHER'])[((i - 1) % 4) + 1],
       TIMESTAMPTZ '2026-07-20 10:00:00+09' + i * INTERVAL '1 hour'
FROM generate_series(1, 12) AS g(i);

INSERT INTO travel_record_reports (
    travel_record_id, reporter_user_id, reason, detail, status, processed_by, processed_at, resolution_note)
VALUES
(1,4,'SPAM','합성 신고 1','PENDING',NULL,NULL,NULL),
(2,5,'ABUSE','합성 신고 2','REVIEWING',NULL,NULL,NULL),
(3,6,'INAPPROPRIATE','합성 신고 3','RESOLVED',9,CURRENT_TIMESTAMP,'관리자 처리'),
(4,7,'COPYRIGHT','합성 신고 4','REJECTED',10,CURRENT_TIMESTAMP,'관리자 반려'),
(1,8,'PRIVACY','합성 신고 5','PENDING',NULL,NULL,NULL),
(2,3,'OTHER','합성 신고 6','RESOLVED',9,CURRENT_TIMESTAMP,'관리자 처리');

SELECT setval(pg_get_serial_sequence('travel_records', 'travel_record_id'), 4, true);
SELECT setval(pg_get_serial_sequence('travel_record_comments', 'travel_record_comment_id'), 24, true);
COMMIT;
