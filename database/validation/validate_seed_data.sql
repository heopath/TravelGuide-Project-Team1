-- All My Trips local seed validation. Each violation query must return zero rows.
USE all_my_trips;
SET NAMES utf8mb4;

-- Expected row counts (travel_records is intentionally 4 pending team-lead resolution).
SELECT 'users' AS item, 10 AS expected, COUNT(*) AS actual FROM users
UNION ALL SELECT 'places',100,COUNT(*) FROM places
UNION ALL SELECT 'place_images',150,COUNT(*) FROM place_images
UNION ALL SELECT 'place_travel_styles',200,COUNT(*) FROM place_travel_styles
UNION ALL SELECT 'trips',15,COUNT(*) FROM trips
UNION ALL SELECT 'trip_days',40,COUNT(*) FROM trip_days
UNION ALL SELECT 'itinerary_items',150,COUNT(*) FROM itinerary_items
UNION ALL SELECT 'ai_generation_requests',20,COUNT(*) FROM ai_generation_requests
UNION ALL SELECT 'recommendation_events',100,COUNT(*) FROM recommendation_events
UNION ALL SELECT 'travel_records',4,COUNT(*) FROM travel_records
UNION ALL SELECT 'travel_record_images',15,COUNT(*) FROM travel_record_images
UNION ALL SELECT 'travel_record_comments',20,COUNT(*) FROM travel_record_comments
UNION ALL SELECT 'travel_record_likes',30,COUNT(*) FROM travel_record_likes
UNION ALL SELECT 'travel_record_shares',10,COUNT(*) FROM travel_record_shares
UNION ALL SELECT 'travel_record_reports',5,COUNT(*) FROM travel_record_reports
UNION ALL SELECT 'ticket_products',20,COUNT(*) FROM ticket_products
UNION ALL SELECT 'reservations',15,COUNT(*) FROM reservations
UNION ALL SELECT 'reservation_items',20,COUNT(*) FROM reservation_items
UNION ALL SELECT 'ticket_validation_logs',20,COUNT(*) FROM ticket_validation_logs;

SELECT r.* FROM travel_records r LEFT JOIN trips t ON t.trip_id=r.trip_id WHERE t.trip_id IS NULL;
SELECT r.* FROM travel_records r JOIN trips t ON t.trip_id=r.trip_id WHERE r.user_id<>t.user_id;
SELECT r.*,t.status FROM travel_records r JOIN trips t ON t.trip_id=r.trip_id WHERE t.status<>'COMPLETED';
SELECT d.* FROM trip_days d JOIN trips t ON t.trip_id=d.trip_id WHERE d.trip_date<t.start_date OR d.trip_date>t.end_date;
SELECT trip_day_id,sort_order,COUNT(*) duplicate_count FROM itinerary_items GROUP BY trip_day_id,sort_order HAVING COUNT(*)>1;
SELECT place_id,SUM(is_primary) primary_count FROM place_images GROUP BY place_id HAVING SUM(is_primary)>1;
SELECT travel_record_id,SUM(is_cover) cover_count FROM travel_record_images GROUP BY travel_record_id HAVING SUM(is_cover)>1;
SELECT c.* FROM travel_record_comments c JOIN travel_record_comments p ON p.travel_record_comment_id=c.parent_comment_id WHERE c.travel_record_id<>p.travel_record_id;
SELECT travel_record_id,user_id,COUNT(*) duplicate_count FROM travel_record_likes GROUP BY travel_record_id,user_id HAVING COUNT(*)>1;
SELECT * FROM ticket_inventory WHERE reserved_quantity>total_quantity;
SELECT * FROM places WHERE category='ACCOMMODATION' AND (website_url IS NULL OR website_url='');
SELECT ri.reservation_item_id,ri.quantity,COUNT(it.issued_ticket_id) issued_count FROM reservation_items ri JOIN reservations r ON r.reservation_id=ri.reservation_id JOIN payments p ON p.reservation_id=r.reservation_id AND p.status='PAID' LEFT JOIN issued_tickets it ON it.reservation_item_id=ri.reservation_item_id GROUP BY ri.reservation_item_id,ri.quantity HAVING COUNT(it.issued_ticket_id)<>ri.quantity;
SELECT email FROM users WHERE password_hash='REPLACE_WITH_TEAM_BCRYPT_HASH'; -- Must return 0 rows before login testing.
