-- 채팅의 검색·정렬·상태 판단에는 쓰지 않는 화면 표현 데이터만 JSONB로 보관한다.
-- 메시지 본문과 방 상태는 기존 정규화 컬럼에 그대로 두므로 운영 쿼리 성능에 영향을 주지 않는다.
CREATE TABLE IF NOT EXISTS support_chat_message_blocks (
    support_chat_message_block_id BIGSERIAL PRIMARY KEY,
    support_chat_message_id BIGINT NOT NULL,
    block_type VARCHAR(40) NOT NULL,
    display_order SMALLINT NOT NULL,
    schema_version SMALLINT NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_support_chat_message_blocks_message
        FOREIGN KEY (support_chat_message_id)
        REFERENCES support_chat_messages (support_chat_message_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_support_chat_message_blocks_order
        UNIQUE (support_chat_message_id, display_order),
    CONSTRAINT ck_support_chat_message_blocks_type
        CHECK (block_type ~ '^[A-Z][A-Z0-9_]{0,39}$'),
    CONSTRAINT ck_support_chat_message_blocks_order
        CHECK (display_order BETWEEN 0 AND 99),
    CONSTRAINT ck_support_chat_message_blocks_version
        CHECK (schema_version BETWEEN 1 AND 99),
    CONSTRAINT ck_support_chat_message_blocks_payload
        CHECK (jsonb_typeof(payload) = 'object')
);

-- support_chat_message_id, display_order 조회는 위 UNIQUE 제약이 만드는 btree 인덱스로 충분하다.
-- 같은 열 순서의 명시적 인덱스를 별도로 만들지 않는다(중복 인덱스는 쓰기 비용만 늘린다).

-- 기존 버튼도 새 클라이언트가 동일한 블록 구조로 읽을 수 있게 한 번만 옮긴다.
-- 원래 컬럼은 앱 롤백 호환성을 위해 지우지 않는다.
INSERT INTO support_chat_message_blocks (
    support_chat_message_id, block_type, display_order, schema_version, payload
)
SELECT m.support_chat_message_id,
       'ACTION_GROUP',
       0,
       1,
       jsonb_build_object(
           'items',
           to_jsonb(array_remove(ARRAY[m.action_key, m.action_key_2, m.action_key_3], NULL))
       )
FROM support_chat_messages m
WHERE COALESCE(m.action_key, m.action_key_2, m.action_key_3) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM support_chat_message_blocks b
      WHERE b.support_chat_message_id = m.support_chat_message_id
        AND b.block_type = 'ACTION_GROUP'
  );
