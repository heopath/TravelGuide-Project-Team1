# Flyway migration location

PostgreSQL Flyway 원본은 저장소 루트 `database/migration`에서 관리한다.
Gradle `processResources` 작업이 V1~V7을 빌드 결과의 `db/migration`으로 복사하므로 이 디렉터리에 SQL 복사본을 직접 추가하지 않는다.
공유 DB에 적용된 버전 파일은 수정하지 않고 다음 버전 마이그레이션을 추가한다.
