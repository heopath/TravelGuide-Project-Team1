-- 상담봇이 설명으로 끝내지 않고, 서비스 안의 다음 화면으로 이어 주는 선택 액션.
-- URL 자체를 저장하지 않고 서버가 허용한 키만 저장해 외부·임의 경로가 섞이지 않게 한다.
ALTER TABLE support_chat_messages
    ADD COLUMN action_key VARCHAR(40);

ALTER TABLE support_chat_messages
    ADD CONSTRAINT ck_support_chat_messages_action
        CHECK (action_key IS NULL OR action_key IN (
            'NEW_TRIP', 'MY_TRIPS', 'TRIP_SCHEDULE', 'RECOMMENDED_PLACES',
            'BOOK_FLIGHT', 'BOOK_HOTEL', 'BOOK_TICKET', 'MY_BOOKINGS',
            'MY_TICKETS', 'FAVORITES', 'REVIEWS', 'NOTIFICATIONS',
            'ACCOUNT_SETTINGS', 'SUPPORT'
        ));
