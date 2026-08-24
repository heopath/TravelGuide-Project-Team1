-- 한 답변에서 다음 행동을 최대 세 가지까지 제안한다.
-- V28의 첫 action_key는 이미 적용된 마이그레이션이므로 그대로 두고 슬롯만 확장한다.
ALTER TABLE support_chat_messages
    ADD COLUMN action_key_2 VARCHAR(40),
    ADD COLUMN action_key_3 VARCHAR(40);

ALTER TABLE support_chat_messages
    ADD CONSTRAINT ck_support_chat_messages_action_2 CHECK (
        action_key_2 IS NULL OR action_key_2 IN (
            'NEW_TRIP', 'MY_TRIPS', 'TRIP_SCHEDULE', 'RECOMMENDED_PLACES',
            'BOOK_FLIGHT', 'BOOK_HOTEL', 'BOOK_TICKET', 'MY_BOOKINGS',
            'MY_TICKETS', 'FAVORITES', 'REVIEWS', 'NOTIFICATIONS',
            'ACCOUNT_SETTINGS', 'SUPPORT'
        )
    ),
    ADD CONSTRAINT ck_support_chat_messages_action_3 CHECK (
        action_key_3 IS NULL OR action_key_3 IN (
            'NEW_TRIP', 'MY_TRIPS', 'TRIP_SCHEDULE', 'RECOMMENDED_PLACES',
            'BOOK_FLIGHT', 'BOOK_HOTEL', 'BOOK_TICKET', 'MY_BOOKINGS',
            'MY_TICKETS', 'FAVORITES', 'REVIEWS', 'NOTIFICATIONS',
            'ACCOUNT_SETTINGS', 'SUPPORT'
        )
    );
