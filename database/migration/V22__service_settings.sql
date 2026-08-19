-- 관리자 화면에서 변경하는 서비스 운영 설정.
-- build.gradle의 version은 빌드 산출물 식별자이고, 이 테이블은 화면 표시값을 관리한다.
CREATE TABLE service_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_by    BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_service_settings_value_not_blank
        CHECK (char_length(btrim(setting_value)) > 0)
);

CREATE TRIGGER trg_service_settings_updated_at
BEFORE UPDATE ON service_settings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO service_settings (setting_key, setting_value)
VALUES ('footer.version', '0.9.0');

COMMENT ON TABLE service_settings IS 'Administrator-managed service display settings';
COMMENT ON COLUMN service_settings.setting_key IS 'Stable application setting key';
COMMENT ON COLUMN service_settings.setting_value IS 'Display value without presentation prefix';
