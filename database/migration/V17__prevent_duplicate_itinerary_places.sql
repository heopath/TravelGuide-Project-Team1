-- 한 DAY 안에서 같은 실제 장소를 여러 번 저장하지 않는다.
-- place_id가 없는 메모/교통/활동 항목은 기존처럼 중복을 허용한다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_itinerary_items_trip_day_place
    ON itinerary_items (trip_day_id, place_id)
    WHERE place_id IS NOT NULL;
