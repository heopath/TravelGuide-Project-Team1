-- 발급 티켓의 QR 토큰. (#265)
--
-- 지금은 입장 코드를 발권 응답에서 한 번만 내려주고 서버는 SHA-256 해시만 들고 있다.
-- 그래서 손님이 화면을 닫으면 다시 볼 방법이 없다. 마이페이지에서 QR을 다시 보려면
-- 코드를 되살릴 수 있어야 하는데, 평문을 저장하면 지금 설계를 통째로 되돌리는 것이 된다.
--
-- 그래서 원래 토큰은 그대로 두고 "대조용 토큰"을 하나 더 둔다. QR을 볼 때마다 새로 만들어
-- 해시만 남기고 평문은 그 응답에만 실어 보낸다. 저장되는 것은 여전히 해시뿐이다.
--
-- 유효기간이 짧은 이유는 QR이 화면 캡처로 퍼지기 때문이다. 사진 한 장이 영원히 통하면
-- 한 장으로 여러 명이 들어간다.

ALTER TABLE issued_tickets
    ADD COLUMN IF NOT EXISTS qr_token_hash CHAR(64),
    ADD COLUMN IF NOT EXISTS qr_token_expires_at TIMESTAMPTZ;

-- 해시가 겹치면 다른 티켓이 열린다. verification_token_hash와 같은 이유로 유일해야 한다.
-- 아직 QR을 만든 적 없는 티켓이 대부분이라 부분 인덱스를 쓴다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_issued_tickets_qr_token_hash
    ON issued_tickets(qr_token_hash) WHERE qr_token_hash IS NOT NULL;

-- 유효기간이 있으면 만료 시각도 있어야 한다. 둘 중 하나만 있으면 검표가 판단할 수 없다.
ALTER TABLE issued_tickets
    ADD CONSTRAINT ck_issued_tickets_qr_token
        CHECK ((qr_token_hash IS NULL) = (qr_token_expires_at IS NULL));
