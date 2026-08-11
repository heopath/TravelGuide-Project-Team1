-- Some local databases used version 8 for a different migration before
-- V8__ai_chat_history_persistence.sql was merged. Recreate the intended
-- indexes safely after the Flyway history has been repaired.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_chat_sessions_active_user_trip
    ON ai_chat_sessions(user_id, trip_id)
    WHERE status = 'ACTIVE' AND trip_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_chat_messages_session_sequence
    ON ai_chat_messages(ai_chat_session_id, sequence_number DESC);
