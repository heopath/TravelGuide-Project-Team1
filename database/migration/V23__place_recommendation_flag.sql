-- 추천장소 노출 여부를 is_active와 분리한다.
--
-- 지금까지 is_active 하나가 두 가지를 겸했다.
--   1) 이 장소 데이터가 유효한가
--   2) 추천장소 화면에 노출할까
--
-- 사용자가 일정에 장소를 담으면 places에 is_active=TRUE로 저장되므로,
-- 담는 즉시 2)가 켜져 관리자가 등록하지 않은 장소까지 추천장소에 노출됐다.
-- 두 판단은 성격이 다르므로 컬럼을 나눈다.
ALTER TABLE places
    ADD COLUMN is_recommended BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN places.is_recommended IS 'Whether the place is shown on the curated recommendation screen (admin decision)';

-- 기존 데이터 백필.
--
-- 이 변경 전까지 추천 목록은 is_active=TRUE인 장소 전부였다. 배포로 목록이 갑자기
-- 비지 않도록 그 상태를 그대로 옮기되, 사용자가 담아서 들어온 것만 뺀다.
--
-- 사용자 유입 경로는 모두 카카오다.
--   - 일정에 장소 담기(schedule.js -> POST /api/v1/places)
--   - AI 일정 저장(AiTripPlanPersistenceService)
--   - 주변 장소 탐색(KakaoPlaceDiscoveryService)
-- 셋 다 external_provider='KAKAO'로 저장된다.
--
-- 나머지(관리자 등록은 NULL, 시드는 LOCAL_SEED)는 노출을 유지한다.
-- "관리자 등록(NULL)만 켠다"로 잡으면 시드 등 다른 provider가 통째로 내려간다.
--
-- 관리자가 카카오에서 온 장소를 추천으로 쓰고 있었다면 이 백필에서 함께 꺼진다.
-- 둘을 구분할 값이 없기 때문이며, 관리자 화면에서 다시 켤 수 있다.
UPDATE places
   SET is_recommended = TRUE
 WHERE (external_provider IS NULL OR external_provider <> 'KAKAO')
   AND is_active = TRUE;

-- 추천 목록 조회가 is_recommended로 거르므로 함께 태운다.
CREATE INDEX idx_places_recommended
    ON places(is_recommended, category, region)
 WHERE is_recommended = TRUE;
