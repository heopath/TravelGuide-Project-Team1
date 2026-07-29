BEGIN;

INSERT INTO ticket_products (
    ticket_product_id, place_id, name, description, sale_start_at, sale_end_at,
    usage_start_date, usage_end_date, status)
SELECT i, CASE WHEN i <= 10 THEN i ELSE 65 + i END,
       '모의 관광 티켓 ' || i, '합성 상품',
       TIMESTAMPTZ '2026-07-01 00:00:00+09', TIMESTAMPTZ '2026-12-20 23:59:59+09',
       DATE '2026-08-01', DATE '2026-12-31', 'ON_SALE'
FROM generate_series(1, 20) AS g(i);

INSERT INTO ticket_product_options (
    ticket_product_option_id, ticket_product_id, name, description, unit_price,
    currency_code, max_quantity_per_user, sort_order, is_active)
SELECT (product_id - 1) * 3 + option_no, product_id,
       (ARRAY['일반 이용권','프리미엄 이용권','패키지 이용권'])[option_no],
       '합성 티켓 옵션', 14000 + product_id * 1000 + option_no * 2000,
       'KRW', 4, option_no, TRUE
FROM generate_series(1, 20) AS p(product_id)
CROSS JOIN generate_series(1, 3) AS o(option_no);

INSERT INTO ticket_time_slots (
    ticket_time_slot_id, ticket_product_option_id, usage_date, start_time, end_time, status)
SELECT option_id, option_id, DATE '2026-09-15',
       CASE ((option_id - 1) % 3) + 1 WHEN 1 THEN TIME '10:00' WHEN 2 THEN TIME '14:00' ELSE TIME '16:30' END,
       CASE ((option_id - 1) % 3) + 1 WHEN 1 THEN TIME '12:00' WHEN 2 THEN TIME '16:00' ELSE TIME '18:30' END,
       'OPEN'
FROM generate_series(1, 60) AS g(option_id);

INSERT INTO ticket_inventory (ticket_time_slot_id, total_quantity, reserved_quantity, version)
SELECT slot_id, 80 + slot_id * 2, 0, 0 FROM generate_series(1, 60) AS g(slot_id);

DO $$
DECLARE
    rid INTEGER;
    item_id INTEGER := 1;
    qty INTEGER;
    product_id INTEGER;
    second_product_id INTEGER;
    option_id INTEGER;
    second_option_id INTEGER;
    unit_price NUMERIC(15,2);
    second_price NUMERIC(15,2);
    total NUMERIC(15,2);
    payment_status VARCHAR(20);
    ticket_id INTEGER := 1;
    n INTEGER;
BEGIN
    FOR rid IN 1..15 LOOP
        qty := (rid % 2) + 1;
        product_id := ((rid - 1) % 20) + 1;
        second_product_id := (rid % 20) + 1;
        option_id := (product_id - 1) * 3 + 2;
        second_option_id := (second_product_id - 1) * 3 + 1;
        unit_price := 14000 + product_id * 1000 + 2 * 2000;
        second_price := 14000 + second_product_id * 1000 + 1 * 2000;
        total := qty * unit_price + CASE WHEN rid <= 5 THEN second_price ELSE 0 END;

        INSERT INTO reservations (
            reservation_id, reservation_number, user_id, status, total_amount, currency_code,
            expires_at, confirmed_at, cancelled_at)
        VALUES (
            rid, 'AMT-2026-' || lpad(rid::text, 5, '0'), ((rid - 1) % 8) + 1,
            CASE WHEN rid <= 5 THEN 'PENDING' WHEN rid <= 12 THEN 'CONFIRMED' ELSE 'CANCELLED' END,
            total, 'KRW',
            CASE WHEN rid <= 5 THEN TIMESTAMPTZ '2026-07-25 12:00:00+09' END,
            CASE WHEN rid BETWEEN 6 AND 12 THEN TIMESTAMPTZ '2026-07-24 11:00:00+09' END,
            CASE WHEN rid >= 13 THEN TIMESTAMPTZ '2026-07-24 11:30:00+09' END);

        INSERT INTO reservation_items (
            reservation_item_id, reservation_id, ticket_time_slot_id, product_name, option_name,
            usage_date, usage_start_time, quantity, unit_price, line_amount)
        VALUES (item_id, rid, option_id, '모의 관광 티켓 ' || product_id, '프리미엄 이용권',
                DATE '2026-09-15', TIME '14:00', qty, unit_price, qty * unit_price);

        IF rid <= 5 THEN
            item_id := item_id + 1;
            INSERT INTO reservation_items (
                reservation_item_id, reservation_id, ticket_time_slot_id, product_name, option_name,
                usage_date, usage_start_time, quantity, unit_price, line_amount)
            VALUES (item_id, rid, second_option_id, '모의 관광 티켓 ' || second_product_id, '일반 이용권',
                    DATE '2026-09-15', TIME '10:00', 1, second_price, second_price);
        END IF;

        payment_status := CASE
            WHEN rid <= 5 THEN CASE WHEN rid % 2 = 0 THEN 'FAILED' ELSE 'READY' END
            WHEN rid <= 12 THEN 'PAID'
            ELSE CASE WHEN rid % 2 = 0 THEN 'REFUNDED' ELSE 'CANCELLED' END
        END;

        INSERT INTO payments (
            payment_id, reservation_id, idempotency_key, provider, provider_payment_key, method,
            status, amount, currency_code, failure_code, failure_message, requested_at,
            approved_at, cancelled_at)
        VALUES (
            rid, rid, 'seed-payment-' || rid, 'MOCK', 'MOCK-PAY-' || lpad(rid::text, 5, '0'),
            'CARD', payment_status, total, 'KRW',
            CASE WHEN payment_status = 'FAILED' THEN 'MOCK_FAILURE' END,
            CASE WHEN payment_status = 'FAILED' THEN '모의 실패' END,
            TIMESTAMPTZ '2026-07-24 10:00:00+09',
            CASE WHEN payment_status = 'PAID' THEN TIMESTAMPTZ '2026-07-24 10:01:00+09' END,
            CASE WHEN payment_status IN ('CANCELLED','REFUNDED') THEN TIMESTAMPTZ '2026-07-24 11:30:00+09' END);

        IF rid BETWEEN 6 AND 12 THEN
            FOR n IN 1..qty LOOP
                INSERT INTO issued_tickets (
                    issued_ticket_id, reservation_item_id, ticket_number, verification_token_hash,
                    issue_method, status, valid_from, valid_until, issued_at)
                VALUES (
                    ticket_id, item_id, 'AMT-TICKET-' || lpad(ticket_id::text, 6, '0'),
                    encode(digest('synthetic-token-' || ticket_id, 'sha256'), 'hex'),
                    CASE WHEN ticket_id % 3 = 0 THEN 'PRINT' ELSE 'MOBILE' END,
                    'ISSUED', TIMESTAMPTZ '2026-09-15 00:00:00+09',
                    TIMESTAMPTZ '2026-09-15 23:59:59+09', TIMESTAMPTZ '2026-07-24 10:02:00+09');
                ticket_id := ticket_id + 1;
            END LOOP;
        END IF;
        item_id := item_id + 1;
    END LOOP;
END;
$$;

UPDATE ticket_inventory inventory
SET reserved_quantity = aggregated.quantity,
    version = 1
FROM (
    SELECT ri.ticket_time_slot_id, SUM(ri.quantity)::integer AS quantity
    FROM reservation_items ri
    JOIN reservations r ON r.reservation_id = ri.reservation_id
    WHERE r.status IN ('PENDING', 'CONFIRMED')
    GROUP BY ri.ticket_time_slot_id
) aggregated
WHERE inventory.ticket_time_slot_id = aggregated.ticket_time_slot_id;

UPDATE issued_tickets SET status = 'USED', used_at = TIMESTAMPTZ '2026-09-15 10:00:00+09' WHERE issued_ticket_id = 1;
UPDATE issued_tickets SET status = 'CANCELLED', cancelled_at = TIMESTAMPTZ '2026-08-01 09:00:00+09' WHERE issued_ticket_id = 2;

INSERT INTO ticket_validation_logs (
    issued_ticket_id, validator_user_id, presented_token_fingerprint, validation_result,
    validation_channel, device_id, failure_reason, metadata, validated_at)
SELECT CASE WHEN i % 5 = 0 THEN NULL ELSE ((i - 1) % 10) + 1 END,
       CASE WHEN i % 3 = 0 THEN 10 ELSE 9 END,
       encode(digest('presented-' || i, 'sha256'), 'hex'),
       (ARRAY['SUCCESS','ALREADY_USED','CANCELLED','EXPIRED','NOT_FOUND'])[((i - 1) % 5) + 1],
       CASE WHEN i % 2 = 0 THEN 'MOCK_SCANNER' ELSE 'ADMIN_WEB' END,
       'mock-device-' || ((i % 3) + 1),
       CASE WHEN (i - 1) % 5 = 0 THEN NULL ELSE '모의 검표 결과' END,
       jsonb_build_object('synthetic', TRUE),
       TIMESTAMPTZ '2026-09-15 10:00:00+09' + i * INTERVAL '1 minute'
FROM generate_series(1, 20) AS g(i);

SELECT setval(pg_get_serial_sequence('ticket_products', 'ticket_product_id'), 20, true);
SELECT setval(pg_get_serial_sequence('ticket_product_options', 'ticket_product_option_id'), 60, true);
SELECT setval(pg_get_serial_sequence('ticket_time_slots', 'ticket_time_slot_id'), 60, true);
SELECT setval(pg_get_serial_sequence('reservations', 'reservation_id'), 15, true);
SELECT setval(pg_get_serial_sequence('reservation_items', 'reservation_item_id'), 20, true);
SELECT setval(pg_get_serial_sequence('payments', 'payment_id'), 15, true);
SELECT setval(pg_get_serial_sequence('issued_tickets', 'issued_ticket_id'), 10, true);
COMMIT;
