-- Prevent duplicate saved places within one travel day.
-- Items without a place_id (memo, transport, manual entry) remain repeatable.
CREATE UNIQUE INDEX IF NOT EXISTS uk_itinerary_items_trip_day_place
    ON itinerary_items (trip_day_id, place_id)
    WHERE place_id IS NOT NULL;
