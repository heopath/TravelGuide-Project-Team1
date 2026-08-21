-- 화면에 보이는 서비스 버전을 0.0.4로 올린다.
--
-- V22가 0.9.0으로 심어 둔 값이다. 코드의 기본값(ServiceVersionService.DEFAULT_VERSION)도
-- 함께 바꿨지만, 이 행이 있으면 그쪽이 이기므로 여기서도 고쳐야 화면이 바뀐다.
--
-- 이미 관리자 화면에서 다른 값으로 바꿔 둔 곳이 있다면 이 마이그레이션이 되돌린다.
-- 운영 DB는 Flyway가 꺼져 있어(SPRING_FLYWAY_ENABLED=false) 손으로 적용하거나,
-- 관리자 화면에서 직접 바꾼다.
UPDATE service_settings
SET setting_value = '0.0.4'
WHERE setting_key = 'footer.version';
