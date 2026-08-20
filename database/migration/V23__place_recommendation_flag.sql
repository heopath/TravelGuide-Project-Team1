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
-- 관리자 등록 경로(AdminPlaceService)는 external_provider를 설정하지 않는다.
-- AdminPlaceRequest DTO에 해당 필드 자체가 없다. 반대로 사용자가 일정에 담는 경로는
-- 카카오 검색 결과이므로 항상 external_provider='KAKAO'가 붙는다.
-- 따라서 아래 조건으로 관리자 등록분만 켤 수 있다.
UPDATE places
   SET is_recommended = TRUE
 WHERE external_provider IS NULL
   AND is_active = TRUE;

-- 추천 목록 조회가 is_recommended로 거르므로 함께 태운다.
CREATE INDEX idx_places_recommended
    ON places(is_recommended, category, region)
 WHERE is_recommended = TRUE;
