-- Gemini 할당량 없이 상담 채팅의 JSONB 액션·장소 카드 UI를 확인하는 로컬 전용 seed다.
-- 운영 DB나 Flyway migration으로 실행하지 않는다.
DO $$
DECLARE
    v_room_id BIGINT;
    v_message_id BIGINT;
    v_places JSONB;
BEGIN
    SELECT r.support_chat_room_id INTO v_room_id
      FROM users u
      JOIN support_chat_rooms r ON r.user_id = u.user_id
     WHERE u.email = 'test-user@allmytrips.dev' AND r.status <> 'CLOSED'
     ORDER BY r.support_chat_room_id DESC LIMIT 1;

    IF v_room_id IS NULL THEN
        RAISE NOTICE '열린 test-user 상담방이 없어 JSONB 채팅 seed를 건너뜁니다.';
        RETURN;
    END IF;

    SELECT jsonb_agg(jsonb_build_object(
               'placeId', place_id, 'name', name, 'category', category,
               'address', COALESCE(address, ''), 'description', COALESCE(description, ''),
               'imageUrl', COALESCE(primary_image_url, ''), 'rating', average_rating,
               'reason', CASE row_number
                   WHEN 1 THEN '도심 여행 동선에 넣기 좋아요'
                   WHEN 2 THEN '다른 지역 일정과 비교해 보기 좋아요'
                   ELSE '여행 일정의 선택지로 확인해 보세요'
               END
           ) ORDER BY row_number) INTO v_places
      FROM (
          SELECT p.*,
                 COALESCE((
                     SELECT pi.image_url FROM place_images pi
                      WHERE pi.place_id = p.place_id
                      ORDER BY pi.is_primary DESC, pi.sort_order, pi.place_image_id LIMIT 1
                 ), '') AS primary_image_url,
                 row_number() OVER (ORDER BY is_recommended DESC NULLS LAST, place_id) AS row_number
            FROM places p WHERE is_active = TRUE
           ORDER BY is_recommended DESC NULLS LAST, place_id LIMIT 3
      ) selected_places;

    IF v_places IS NULL OR jsonb_array_length(v_places) = 0 THEN
        RAISE NOTICE '활성 장소가 없어 JSONB 채팅 seed를 건너뜁니다.';
        RETURN;
    END IF;

    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 추천 장소를 카드로 확인해 보세요.'
     LIMIT 1;

    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (
            support_chat_room_id, sender_type, sender_user_id, content, action_key, action_key_2
        ) VALUES (
            v_room_id, 'BOT', NULL, '[로컬 UI 검증] 추천 장소를 카드로 확인해 보세요.',
            'RECOMMENDED_PLACES', 'NEW_TRIP'
        ) RETURNING support_chat_message_id INTO v_message_id;

        INSERT INTO support_chat_message_blocks (
            support_chat_message_id, block_type, display_order, schema_version, payload
        ) VALUES
            (v_message_id, 'ACTION_GROUP', 0, 1,
             jsonb_build_object('items', jsonb_build_array('RECOMMENDED_PLACES', 'NEW_TRIP'))),
            (v_message_id, 'PLACE_CARDS', 1, 1, jsonb_build_object('items', v_places));

        UPDATE support_chat_rooms SET last_message_at = CURRENT_TIMESTAMP
         WHERE support_chat_room_id = v_room_id;
    END IF;

    RAISE NOTICE '상담 JSONB UI seed message id=%', v_message_id;
END
$$;

-- PLACE_CARDS·ACTION_GROUP의 필드 조합(이미지/평점/설명 유무, 버튼 개수)을 눈으로 비교하기
-- 위한 변형 seed다. 운영 DB나 Flyway migration으로 실행하지 않는다.
DO $$
DECLARE
    v_room_id BIGINT;
    v_message_id BIGINT;
BEGIN
    SELECT r.support_chat_room_id INTO v_room_id
      FROM users u
      JOIN support_chat_rooms r ON r.user_id = u.user_id
     WHERE u.email = 'test-user@allmytrips.dev' AND r.status <> 'CLOSED'
     ORDER BY r.support_chat_room_id DESC LIMIT 1;

    IF v_room_id IS NULL THEN
        RAISE NOTICE '열린 test-user 상담방이 없어 카드 스타일 변형 seed를 건너뜁니다.';
        RETURN;
    END IF;

    -- 1) 이미지 없는 카드
    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 이미지 없는 카드예요.' LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (support_chat_room_id, sender_type, sender_user_id, content)
        VALUES (v_room_id, 'BOT', NULL, '[로컬 UI 검증] 이미지 없는 카드예요.')
        RETURNING support_chat_message_id INTO v_message_id;
        INSERT INTO support_chat_message_blocks (support_chat_message_id, block_type, display_order, schema_version, payload)
        VALUES (v_message_id, 'PLACE_CARDS', 0, 1, jsonb_build_object('items', jsonb_build_array(
            jsonb_build_object('placeId', 4, 'name', '강릉 ATTRACTION 004', 'category', 'ATTRACTION',
                'address', '강릉 테스트로 4', 'description', '강릉 합성 장소', 'imageUrl', '',
                'rating', 3.6, 'reason', '이미지가 없어도 카드가 잘 보이는지 확인해요')
        )));
    END IF;

    -- 2) 평점 없는 카드
    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 평점 없는 카드예요.' LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (support_chat_room_id, sender_type, sender_user_id, content)
        VALUES (v_room_id, 'BOT', NULL, '[로컬 UI 검증] 평점 없는 카드예요.')
        RETURNING support_chat_message_id INTO v_message_id;
        INSERT INTO support_chat_message_blocks (support_chat_message_id, block_type, display_order, schema_version, payload)
        VALUES (v_message_id, 'PLACE_CARDS', 0, 1, jsonb_build_object('items', jsonb_build_array(
            jsonb_build_object('placeId', 5, 'name', '경주 ATTRACTION 005', 'category', 'ATTRACTION',
                'address', '경주 테스트로 5', 'description', '경주 합성 장소',
                'imageUrl', 'https://picsum.photos/seed/all-my-trips-place-5-1/1200/800',
                'reason', '평점 없이도 카드가 정상인지 확인해요')
        )));
    END IF;

    -- 3) 설명 없는 카드(추천 이유만 있음)
    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 설명 없는 카드예요.' LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (support_chat_room_id, sender_type, sender_user_id, content)
        VALUES (v_room_id, 'BOT', NULL, '[로컬 UI 검증] 설명 없는 카드예요.')
        RETURNING support_chat_message_id INTO v_message_id;
        INSERT INTO support_chat_message_blocks (support_chat_message_id, block_type, display_order, schema_version, payload)
        VALUES (v_message_id, 'PLACE_CARDS', 0, 1, jsonb_build_object('items', jsonb_build_array(
            jsonb_build_object('placeId', 6, 'name', '전주 ATTRACTION 006', 'category', 'ATTRACTION',
                'address', '전주 테스트로 6',
                'imageUrl', 'https://picsum.photos/seed/all-my-trips-place-6-1/1200/800',
                'rating', 3.8, 'reason', '추천 이유만 있고 별도 설명은 없는 경우예요')
        )));
    END IF;

    -- 4) 최소 정보만 있는 카드(이유·설명·이미지·평점 전부 없음 → "자세히 보기" 대체 문구 확인)
    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 최소 정보만 있는 카드예요.' LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (support_chat_room_id, sender_type, sender_user_id, content)
        VALUES (v_room_id, 'BOT', NULL, '[로컬 UI 검증] 최소 정보만 있는 카드예요.')
        RETURNING support_chat_message_id INTO v_message_id;
        INSERT INTO support_chat_message_blocks (support_chat_message_id, block_type, display_order, schema_version, payload)
        VALUES (v_message_id, 'PLACE_CARDS', 0, 1, jsonb_build_object('items', jsonb_build_array(
            jsonb_build_object('placeId', 7, 'name', '여수 ATTRACTION 007', 'category', 'ATTRACTION',
                'address', '여수 테스트로 7')
        )));
    END IF;

    -- 5) 긴 텍스트로 줄바꿈·레이아웃 확인
    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 긴 텍스트 카드예요.' LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (support_chat_room_id, sender_type, sender_user_id, content)
        VALUES (v_room_id, 'BOT', NULL, '[로컬 UI 검증] 긴 텍스트 카드예요.')
        RETURNING support_chat_message_id INTO v_message_id;
        INSERT INTO support_chat_message_blocks (support_chat_message_id, block_type, display_order, schema_version, payload)
        VALUES (v_message_id, 'PLACE_CARDS', 0, 1, jsonb_build_object('items', jsonb_build_array(
            jsonb_build_object('placeId', 8, 'name', '인천 대표 전망대 & 야경 명소 롱네임 카드 UI 테스트',
                'category', 'ATTRACTION', 'address', '인천 테스트로 8, 아주 긴 주소 표기 테스트 지번까지 포함',
                'imageUrl', 'https://picsum.photos/seed/all-my-trips-place-8-1/1200/800', 'rating', 4.0,
                'reason', '추천 이유 문구가 길어질 때 카드 안에서 줄바꿈이 자연스러운지 확인하는 예시예요',
                'description', '설명 문단이 여러 줄에 걸쳐 길게 이어질 때도 카드 레이아웃이 깨지지 않고 읽기 좋게 유지되는지 확인하기 위한 긴 설명 텍스트입니다. 실제 장소 설명보다 훨씬 길게 작성했어요.')
        )));
    END IF;

    -- 6) 액션 버튼 1개
    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 버튼 1개짜리 안내예요.' LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (support_chat_room_id, sender_type, sender_user_id, content, action_key)
        VALUES (v_room_id, 'BOT', NULL, '[로컬 UI 검증] 버튼 1개짜리 안내예요.', 'MY_BOOKINGS')
        RETURNING support_chat_message_id INTO v_message_id;
        INSERT INTO support_chat_message_blocks (support_chat_message_id, block_type, display_order, schema_version, payload)
        VALUES (v_message_id, 'ACTION_GROUP', 0, 1, jsonb_build_object('items', jsonb_build_array('MY_BOOKINGS')));
    END IF;

    -- 7) 액션 버튼 3개(기존 seed와 다른 조합 — 예약 계열)
    SELECT support_chat_message_id INTO v_message_id
      FROM support_chat_messages
     WHERE support_chat_room_id = v_room_id
       AND content = '[로컬 UI 검증] 예약 관련 버튼 3개 조합이에요.' LIMIT 1;
    IF v_message_id IS NULL THEN
        INSERT INTO support_chat_messages (
            support_chat_room_id, sender_type, sender_user_id, content, action_key, action_key_2, action_key_3
        ) VALUES (
            v_room_id, 'BOT', NULL, '[로컬 UI 검증] 예약 관련 버튼 3개 조합이에요.',
            'BOOK_FLIGHT', 'BOOK_HOTEL', 'BOOK_TICKET'
        ) RETURNING support_chat_message_id INTO v_message_id;
        INSERT INTO support_chat_message_blocks (support_chat_message_id, block_type, display_order, schema_version, payload)
        VALUES (v_message_id, 'ACTION_GROUP', 0, 1,
            jsonb_build_object('items', jsonb_build_array('BOOK_FLIGHT', 'BOOK_HOTEL', 'BOOK_TICKET')));
    END IF;

    UPDATE support_chat_rooms SET last_message_at = CURRENT_TIMESTAMP WHERE support_chat_room_id = v_room_id;

    RAISE NOTICE '카드 스타일 변형 seed 완료(room_id=%)', v_room_id;
END
$$;
