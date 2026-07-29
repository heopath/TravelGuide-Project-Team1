BEGIN;

INSERT INTO ai_chat_sessions (ai_chat_session_id, user_id, trip_id, title, status, last_message_at)
SELECT i, i, i, '합성 AI 가이드 대화 ' || i, 'ACTIVE',
       TIMESTAMPTZ '2026-07-24 11:00:00+09' + i * INTERVAL '1 minute'
FROM generate_series(1, 5) AS g(i);

WITH request_source AS (
    SELECT i,
           (ARRAY['SUCCEEDED','SUCCEEDED','SUCCEEDED','FAILED','PENDING','PROCESSING'])[((i - 1) % 6) + 1] AS request_status,
           (ARRAY['CREATE_ITINERARY','OPTIMIZE_ROUTE','CHAT'])[((i - 1) % 3) + 1] AS request_type
    FROM generate_series(1, 20) AS g(i)
)
INSERT INTO ai_generation_requests (
    ai_generation_request_id, user_id, trip_id, ai_chat_session_id, request_type, provider,
    model_name, prompt_version, input_payload, output_payload, status, error_message,
    input_tokens, output_tokens, requested_at, completed_at)
SELECT i, ((i - 1) % 8) + 1, CASE WHEN i <= 15 THEN i END,
       CASE WHEN request_type = 'CHAT' THEN ((i - 1) % 5) + 1 END,
       request_type, 'MOCK', 'mock-travel-model', 'v1',
       jsonb_build_object('requestNo', i, 'synthetic', TRUE),
       CASE WHEN request_status = 'SUCCEEDED' THEN jsonb_build_object('summary', '합성 응답 ' || i) END,
       request_status, CASE WHEN request_status = 'FAILED' THEN '모의 실패' END,
       100 + i, CASE WHEN request_status = 'SUCCEEDED' THEN 50 + i END,
       TIMESTAMPTZ '2026-07-24 09:00:00+09' + i * INTERVAL '1 minute',
       CASE WHEN request_status IN ('SUCCEEDED','FAILED')
            THEN TIMESTAMPTZ '2026-07-24 10:00:00+09' + i * INTERVAL '1 minute' END
FROM request_source;

INSERT INTO ai_chat_messages (ai_chat_message_id, ai_chat_session_id, ai_generation_request_id, role,
                              content, source_metadata, sequence_number, created_at)
SELECT (session_id - 1) * 2 + seq, session_id,
       CASE WHEN seq = 2 THEN (ARRAY[6,12,3,9,15])[session_id] END,
       CASE WHEN seq = 1 THEN 'USER' ELSE 'ASSISTANT' END,
       CASE WHEN seq = 1 THEN '근처에서 저녁 먹을 곳 추천해줘.' ELSE '현재 일정과 장소 데이터를 바탕으로 합성 추천을 제공합니다.' END,
       CASE WHEN seq = 2 THEN jsonb_build_object('synthetic', TRUE, 'placeIds', jsonb_build_array(session_id)) END,
       seq, TIMESTAMPTZ '2026-07-24 11:00:00+09' + ((session_id * 2 + seq) * INTERVAL '1 minute')
FROM generate_series(1, 5) AS s(session_id)
CROSS JOIN generate_series(1, 2) AS q(seq);

INSERT INTO recommendation_sessions (
    recommendation_session_id, user_id, trip_id, ai_generation_request_id, source, status,
    requested_count, created_at, expires_at)
SELECT i, ((i - 1) % 8) + 1, ((i - 1) % 15) + 1, ((i - 1) % 20) + 1,
       CASE WHEN i % 2 = 0 THEN 'HYBRID' ELSE 'AI' END, 'SERVED', 10,
       TIMESTAMPTZ '2026-07-24 12:00:00+09' + i * INTERVAL '1 minute',
       TIMESTAMPTZ '2026-07-31 12:00:00+09' + i * INTERVAL '1 minute'
FROM generate_series(1, 20) AS g(i);

INSERT INTO recommendation_results (
    recommendation_result_id, recommendation_session_id, place_id, rank, score, reason)
SELECT (session_id - 1) * 10 + rank, session_id,
       (((session_id * 11 + rank * 7) - 1) % 100) + 1,
       rank, 0.95 - rank * 0.04, '여행 스타일과 일정 지역을 반영한 합성 추천'
FROM generate_series(1, 20) AS s(session_id)
CROSS JOIN generate_series(1, 10) AS r(rank);

INSERT INTO recommendation_events (
    recommendation_event_id, user_id, recommendation_result_id, event_type, metadata, occurred_at)
SELECT i, (((((i - 1) % 200) / 10)::integer) % 8) + 1, ((i - 1) % 200) + 1,
       (ARRAY['IMPRESSION','IMPRESSION','CLICK','FAVORITE','ADD_TO_TRIP','DISMISS'])[((i - 1) % 6) + 1],
       jsonb_build_object('synthetic', TRUE, 'surface', 'RECOMMENDATION_PAGE'),
       TIMESTAMPTZ '2026-07-24 13:00:00+09' + i * INTERVAL '1 second'
FROM generate_series(1, 120) AS g(i);

SELECT setval(pg_get_serial_sequence('ai_chat_sessions', 'ai_chat_session_id'), 5, true);
SELECT setval(pg_get_serial_sequence('ai_generation_requests', 'ai_generation_request_id'), 20, true);
SELECT setval(pg_get_serial_sequence('ai_chat_messages', 'ai_chat_message_id'), 10, true);
SELECT setval(pg_get_serial_sequence('recommendation_sessions', 'recommendation_session_id'), 20, true);
SELECT setval(pg_get_serial_sequence('recommendation_results', 'recommendation_result_id'), 200, true);
SELECT setval(pg_get_serial_sequence('recommendation_events', 'recommendation_event_id'), 120, true);
COMMIT;
