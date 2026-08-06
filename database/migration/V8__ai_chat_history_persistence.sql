-- Store one active AI guide conversation per user and trip.
-- Messages already use ON DELETE CASCADE through ai_chat_sessions.
CREATE UNIQUE INDEX uq_ai_chat_sessions_active_user_trip
    ON ai_chat_sessions(user_id, trip_id)
    WHERE status = 'ACTIVE' AND trip_id IS NOT NULL;

CREATE INDEX idx_ai_chat_messages_session_sequence
    ON ai_chat_messages(ai_chat_session_id, sequence_number DESC);
