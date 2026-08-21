-- 티켓 판매 유형을 나눈다. (#256)
--
-- 지금까지 sale_start_at 하나가 두 가지를 겸했다.
--   1) 일반 판매의 `판매 시작` — 그 전에는 목록에 안 나온다
--   2) 지정 시각 판매의 `오픈 순간` — 그 전에도 목록에 나와야 한다(예고)
--
-- 화면은 둘을 다르게 안내해야 하는데 구분할 근거가 없었다. 지정 시각 판매는
-- 미리 보여야 손님이 그 시각에 모이므로, 유형을 컬럼으로 남긴다.
--
-- 기존 상품은 전부 NORMAL이 되어 지금 동작이 그대로 유지된다.
ALTER TABLE ticket_products
    ADD COLUMN sale_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE ticket_products
    ADD CONSTRAINT ck_ticket_products_sale_type
        CHECK (sale_type IN ('NORMAL', 'SCHEDULED'));

-- 오픈 예정 상품을 목록에서 뽑을 때 sale_type과 판매 기간을 함께 본다.
CREATE INDEX idx_ticket_products_sale_type_start
    ON ticket_products (sale_type, sale_start_at);
