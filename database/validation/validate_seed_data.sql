-- PostgreSQL local seed validation.
-- The first result set shows expected and actual counts. Every following violation query must return zero rows.

SELECT * FROM (VALUES
    ('users', 10, (SELECT COUNT(*) FROM users)),
    ('places', 100, (SELECT COUNT(*) FROM places)),
    ('place_images', 200, (SELECT COUNT(*) FROM place_images)),
    ('place_travel_styles', 200, (SELECT COUNT(*) FROM place_travel_styles)),
    ('travel_themes', 4, (SELECT COUNT(*) FROM travel_themes)),
    ('travel_theme_places', 24, (SELECT COUNT(*) FROM travel_theme_places)),
    ('trips', 15, (SELECT COUNT(*) FROM trips)),
    ('trip_days', 40, (SELECT COUNT(*) FROM trip_days)),
    ('itinerary_items', 160, (SELECT COUNT(*) FROM itinerary_items)),
    ('ai_chat_sessions', 5, (SELECT COUNT(*) FROM ai_chat_sessions)),
    ('ai_chat_messages', 10, (SELECT COUNT(*) FROM ai_chat_messages)),
    ('ai_generation_requests', 20, (SELECT COUNT(*) FROM ai_generation_requests)),
    ('recommendation_sessions', 20, (SELECT COUNT(*) FROM recommendation_sessions)),
    ('recommendation_results', 200, (SELECT COUNT(*) FROM recommendation_results)),
    ('recommendation_events', 120, (SELECT COUNT(*) FROM recommendation_events)),
    ('travel_records', 4, (SELECT COUNT(*) FROM travel_records)),
    ('travel_record_images', 16, (SELECT COUNT(*) FROM travel_record_images)),
    ('travel_record_comments', 24, (SELECT COUNT(*) FROM travel_record_comments)),
    ('travel_record_likes', 32, (SELECT COUNT(*) FROM travel_record_likes)),
    ('travel_record_shares', 12, (SELECT COUNT(*) FROM travel_record_shares)),
    ('travel_record_reports', 6, (SELECT COUNT(*) FROM travel_record_reports)),
    ('ticket_products', 20, (SELECT COUNT(*) FROM ticket_products)),
    ('ticket_product_options', 60, (SELECT COUNT(*) FROM ticket_product_options)),
    ('ticket_time_slots', 60, (SELECT COUNT(*) FROM ticket_time_slots)),
    ('reservations', 15, (SELECT COUNT(*) FROM reservations)),
    ('reservation_items', 20, (SELECT COUNT(*) FROM reservation_items)),
    ('ticket_validation_logs', 20, (SELECT COUNT(*) FROM ticket_validation_logs))
) AS counts(item, expected, actual)
ORDER BY item;

SELECT r.* FROM travel_records r LEFT JOIN trips t ON t.trip_id = r.trip_id WHERE t.trip_id IS NULL;
SELECT r.* FROM travel_records r JOIN trips t ON t.trip_id = r.trip_id WHERE r.user_id <> t.user_id;
SELECT r.*, t.status FROM travel_records r JOIN trips t ON t.trip_id = r.trip_id WHERE t.status <> 'COMPLETED';
SELECT d.* FROM trip_days d JOIN trips t ON t.trip_id = d.trip_id WHERE d.trip_date < t.start_date OR d.trip_date > t.end_date;
SELECT trip_day_id, sort_order, COUNT(*) FROM itinerary_items GROUP BY trip_day_id, sort_order HAVING COUNT(*) > 1;
SELECT place_id, COUNT(*) FILTER (WHERE is_primary) FROM place_images GROUP BY place_id HAVING COUNT(*) FILTER (WHERE is_primary) > 1;
SELECT travel_record_id, COUNT(*) FILTER (WHERE is_cover) FROM travel_record_images GROUP BY travel_record_id HAVING COUNT(*) FILTER (WHERE is_cover) > 1;
SELECT c.* FROM travel_record_comments c JOIN travel_record_comments p ON p.travel_record_comment_id = c.parent_comment_id WHERE c.travel_record_id <> p.travel_record_id;
SELECT m.* FROM ai_chat_messages m JOIN ai_generation_requests r ON r.ai_generation_request_id = m.ai_generation_request_id WHERE r.ai_chat_session_id <> m.ai_chat_session_id;
SELECT e.* FROM recommendation_events e JOIN recommendation_results rr ON rr.recommendation_result_id = e.recommendation_result_id JOIN recommendation_sessions rs ON rs.recommendation_session_id = rr.recommendation_session_id WHERE e.user_id <> rs.user_id;
SELECT ttp.* FROM travel_theme_places ttp LEFT JOIN places p ON p.place_id = ttp.place_id WHERE p.place_id IS NULL;
SELECT * FROM ticket_inventory WHERE reserved_quantity > total_quantity;
SELECT ri.* FROM reservation_items ri JOIN ticket_time_slots ts ON ts.ticket_time_slot_id = ri.ticket_time_slot_id WHERE ri.usage_date <> ts.usage_date OR ri.usage_start_time IS DISTINCT FROM ts.start_time;
SELECT ri.reservation_item_id, ri.quantity, COUNT(it.issued_ticket_id) AS issued_count
FROM reservation_items ri
JOIN reservations r ON r.reservation_id = ri.reservation_id
JOIN payments p ON p.reservation_id = r.reservation_id AND p.status = 'PAID'
LEFT JOIN issued_tickets it ON it.reservation_item_id = ri.reservation_item_id
GROUP BY ri.reservation_item_id, ri.quantity
HAVING COUNT(it.issued_ticket_id) <> ri.quantity;
SELECT email FROM users WHERE password_hash = 'REPLACE_WITH_TEAM_BCRYPT_HASH'; -- 10 rows before BCrypt replacement, 0 afterward.
