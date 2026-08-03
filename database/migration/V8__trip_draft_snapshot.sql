-- Persist anonymous and signed-in trip-builder progress before it is normalized into trips.

CREATE TABLE trip_drafts (
    draft_id       UUID PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'SAVED',
    draft_payload  JSONB NOT NULL,
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_trip_drafts_status
        CHECK (status IN ('SAVED', 'CONVERTED', 'EXPIRED')),
    CONSTRAINT ck_trip_drafts_payload_object
        CHECK (jsonb_typeof(draft_payload) = 'object'),
    CONSTRAINT ck_trip_drafts_expiry
        CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX idx_trip_drafts_user_updated
    ON trip_drafts(user_id, updated_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_trip_drafts_expiry
    ON trip_drafts(expires_at)
    WHERE expires_at IS NOT NULL;

CREATE TRIGGER trg_trip_drafts_updated_at
BEFORE UPDATE ON trip_drafts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE trip_drafts IS
    'JSON snapshots for resumable trip-builder progress before trip confirmation';
