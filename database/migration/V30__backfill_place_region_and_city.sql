-- 자동 수집된 장소의 region·city를 주소에서 되채운다.
--
-- PlaceMapper.upsert의 INSERT 컬럼 목록에 region·city가 빠져 있었다. 관리자 등록이
-- 쓰는 insert에는 두 컬럼이 있었으나, 자동 수집 경로는 전부 upsert를 탄다.
--   - KakaoPlaceDiscoveryService (AI 채팅이 카카오에서 찾아 저장)
--   - AiTripPlanPersistenceService (AI 여행계획 저장)
-- 그래서 이 두 경로로 들어온 장소는 값이 NULL로 쌓였다. ON CONFLICT DO UPDATE에도
-- 두 컬럼이 없어서, 같은 장소를 몇 번 다시 수집해도 NULL인 채로 남았다.
--
-- 지역 필터 조회가 `AND region = #{region}`이므로, 이 행들은 지역을 지정한 검색
-- 결과에서 통째로 빠져 있었다. idx_places_search(country_code, region, city, ...)와
-- idx_places_recommended(is_recommended, category, region)도 무력화된 상태였다.
--
-- 끊는 규칙은 KoreanAddress(Java)·kakaoAddressAreas(schedule.js)와 같다.
--   region = 첫 토큰                       "서울특별시 성동구 성수동1가 10" -> "서울특별시"
--   city   = 둘째 토큰, 단 시·군·구로 끝날 때만  같은 주소 -> "성동구"
-- 둘째 토큰의 접미사를 확인하는 이유는 세종특별자치시처럼 시·군·구가 없는 주소에서
-- 둘째 토큰이 도로명("한누리대로")이기 때문이다. 그런 주소는 city를 NULL로 남긴다.
--
-- 이미 값이 있는 행은 건드리지 않는다. 관리자가 화면에서 고쳐 둔 값을 주소 파싱
-- 결과로 되돌리면 안 된다. 빈 문자열은 값이 없는 것으로 본다 -- 예전 schedule.js가
-- 주소가 없을 때 ""를 보내서 NULL과 빈 문자열이 섞여 있다.
--
-- 주소가 없는 행은 채울 근거가 없어 NULL로 남는다. 그 행들은 앞으로 재수집될 때
-- upsert의 COALESCE가 채운다.
--
-- 국내 주소 규칙이므로 country_code = 'KR'로 한정한다.
--
-- trg_places_updated_at이 BEFORE UPDATE로 걸려 있어 대상 행의 updated_at이 이
-- 마이그레이션 시각으로 갱신된다. places.updated_at을 정렬·노출에 쓰는 곳은 없다.
--
-- 채울 값을 먼저 만들고, 실제로 달라지는 행만 UPDATE한다. 주소가 있어도 city를
-- 못 끊는 행(세종처럼 시·군·구가 없는 주소)이 남기 때문에, 대상을 "값이 비었나"로
-- 잡으면 다시 실행할 때마다 같은 행을 값 변화 없이 계속 건드린다.
WITH parsed AS (
    SELECT place_id,
           regexp_split_to_array(btrim(address), '\s+') AS parts,
           NULLIF(btrim(region), '') AS current_region,
           NULLIF(btrim(city), '') AS current_city
      FROM places
     WHERE country_code = 'KR'
       AND address IS NOT NULL
       AND btrim(address) <> ''
), target AS (
    SELECT place_id,
           COALESCE(current_region, NULLIF(parts[1], '')) AS region,
           COALESCE(current_city,
                    CASE WHEN parts[2] ~ '(시|군|구)$' THEN parts[2] END) AS city
      FROM parsed
)
UPDATE places p
   SET region = target.region,
       city = target.city
  FROM target
 WHERE p.place_id = target.place_id
   AND (p.region IS DISTINCT FROM target.region OR p.city IS DISTINCT FROM target.city);

-- 주소가 없어 못 채운 행까지 빈 문자열로 남겨 두면 NULL 검사와 빈 문자열 검사가
-- 갈린다. 이후 코드가 NULL 하나만 보도록 정리한다.
UPDATE places
   SET region = NULLIF(btrim(region), ''),
       city = NULLIF(btrim(city), '')
 WHERE (region IS NOT NULL AND btrim(region) = '')
    OR (city IS NOT NULL AND btrim(city) = '');
