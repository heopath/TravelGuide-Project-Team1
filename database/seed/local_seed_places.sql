-- Local-only place data used for search and RAG indexing tests.
-- Prerequisite: travel_styles IDs 1 through 5 must exist.

BEGIN;

WITH source AS (
    SELECT i,
           CASE WHEN i <= 25 THEN 'ATTRACTION'
                WHEN i <= 45 THEN 'RESTAURANT'
                WHEN i <= 60 THEN 'CAFE'
                WHEN i <= 75 THEN 'ACCOMMODATION'
                WHEN i <= 80 THEN 'FESTIVAL'
                WHEN i <= 90 THEN 'ACTIVITY'
                ELSE 'TRANSPORT' END AS category,
           (ARRAY[
               U&'\C11C\C6B8',
               U&'\BD80\C0B0',
               U&'\C81C\C8FC',
               U&'\AC15\B989',
               U&'\ACBD\C8FC',
               U&'\C804\C8FC',
               U&'\C5EC\C218',
               U&'\C778\CC9C'
           ])[((i - 1) % 8) + 1] AS region_name
    FROM generate_series(1, 100) AS g(i)
)
INSERT INTO public.places (
    place_id, external_provider, external_place_id, category, name, country_code,
    region, city, address, latitude, longitude, description, phone, website_url,
    average_rating, is_active
)
SELECT
    i,
    'LOCAL_SEED',
    'PLACE-' || lpad(i::text, 3, '0'),
    category,
    region_name || ' ' || category || ' ' || lpad(i::text, 3, '0'),
    'KR',
    region_name,
    region_name,
    region_name || ' Test Road ' || i,
    33.2 + (i % 40) * 0.12,
    126.1 + (i % 50) * 0.11,
    region_name || ' location for local RAG search tests.',
    '02-0000-0000',
    'https://example.invalid/places/' || i,
    3.2 + (i % 18) * 0.1,
    TRUE
FROM source
ON CONFLICT (external_provider, external_place_id) DO UPDATE
SET category = EXCLUDED.category,
    name = EXCLUDED.name,
    region = EXCLUDED.region,
    city = EXCLUDED.city,
    address = EXCLUDED.address,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    description = EXCLUDED.description,
    is_active = TRUE;

INSERT INTO public.place_images (place_id, image_url, alt_text, sort_order, is_primary)
SELECT
    p.i,
    'https://picsum.photos/seed/all-my-trips-place-' || p.i || '/1200/800',
    'RAG test place image',
    1,
    TRUE
FROM generate_series(1, 100) AS p(i)
ON CONFLICT (place_id, sort_order) DO UPDATE
SET image_url = EXCLUDED.image_url,
    alt_text = EXCLUDED.alt_text,
    is_primary = TRUE;

WITH place_category AS (
    SELECT place_id, category
    FROM public.places
    WHERE external_provider = 'LOCAL_SEED'
), style_pairs AS (
    SELECT place_id,
           CASE category
               WHEN 'RESTAURANT' THEN 2
               WHEN 'CAFE' THEN 5
               WHEN 'ACCOMMODATION' THEN 1
               WHEN 'ACTIVITY' THEN 4
               ELSE 1
           END AS travel_style_id
    FROM place_category
)
INSERT INTO public.place_travel_styles (place_id, travel_style_id, relevance_score, source)
SELECT place_id, travel_style_id, 85, 'MANUAL'
FROM style_pairs
ON CONFLICT (place_id, travel_style_id) DO UPDATE
SET relevance_score = EXCLUDED.relevance_score,
    source = EXCLUDED.source;

SELECT setval(pg_get_serial_sequence('public.places', 'place_id'), 100, true);

COMMIT;
