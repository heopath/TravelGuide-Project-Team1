-- 관리자 화면에서 교체하는 외부 API 키.
--
-- service_settings에 얹지 않고 테이블을 나눈 이유는 두 가지다.
--   1) setting_value가 VARCHAR(255)인데, 키를 암호화하고 Base64로 감싸면 이 한계를 넘는다.
--   2) 화면에 그대로 노출해도 되는 값(푸터 버전)과 절대 노출하면 안 되는 값을 같은 테이블에
--      두면, 조회 코드 한 줄만 잘못 써도 키가 응답에 실린다.
--
-- encrypted_value에는 평문을 넣지 않는다. AES-GCM으로 봉한 뒤 Base64로 인코딩한 값만 들어간다.
-- 봉인을 여는 마스터 키는 DB가 아니라 서버 환경변수(API_KEY_ENCRYPTION_KEY)에 둔다.
-- DB 덤프만 유출돼도 키를 읽을 수 없게 하기 위한 것이다.
CREATE TABLE api_key_settings (
    api_key_name    VARCHAR(50) PRIMARY KEY,
    encrypted_value TEXT NOT NULL,
    updated_by      BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_api_key_settings_value_not_blank
        CHECK (char_length(btrim(encrypted_value)) > 0)
);

CREATE TRIGGER trg_api_key_settings_updated_at
BEFORE UPDATE ON api_key_settings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE api_key_settings IS 'Administrator-managed external API keys (encrypted at rest)';
COMMENT ON COLUMN api_key_settings.api_key_name IS 'ManagedApiKey enum name';
COMMENT ON COLUMN api_key_settings.encrypted_value IS 'Base64(IV + AES-GCM ciphertext), never plaintext';
