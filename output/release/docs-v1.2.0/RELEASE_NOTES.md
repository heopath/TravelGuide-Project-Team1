# All My Trips PostgreSQL DB 설계 및 실행 문서 v1.2.0

> ⚠️ Draft: `jeomseon0516` 브랜치 기준입니다. PostgreSQL 실제 실행 검증과 `main` 병합 후 대상 커밋을 확인한 뒤 Publish해야 합니다.

## 주요 변경 사항

- 데이터베이스 기준을 MySQL에서 PostgreSQL 16으로 전환했습니다.
- Flyway 마이그레이션 `V1`~`V7`과 통합 스키마 DDL을 PostgreSQL 문법으로 정리했습니다.
- 39개 업무 테이블과 371개 컬럼의 구조 및 관계를 문서화했습니다.
- 페이지 명세와 README를 대조해 회원·장소·여행·추천/AI·여행 기록·관리자·티켓/예약 도메인을 반영했습니다.
- 로컬 seed 데이터와 정합성 검증 SQL을 함께 제공합니다.
- 관리자 권한이 필요한 PostgreSQL 확장 설치 SQL을 애플리케이션 마이그레이션과 분리했습니다.

## 실행 순서

1. `database/init/create_database.sql`
2. 대상 DB 접속 후 `database/init/create_extensions.sql`
3. `database/migration/V1__member_and_place.sql`부터 `V7__ticket_reservation.sql`까지 순서대로 실행
4. `database/seed/local_seed_users.sql`부터 나머지 seed 파일 실행
5. `database/validation/validate_seed_data.sql` 실행

통합 DDL을 사용할 때는 `database/schema/all_my_trips_schema.sql`을 사용하며, 동일한 DB에 Flyway 마이그레이션과 중복 실행하지 않습니다.

## 마이그레이션 기준

- 대상 PostgreSQL DB가 비어 있는 상태를 전제로 `V1`~`V7`을 새 기준선으로 작성했습니다.
- 최초 적용이 끝난 뒤에는 기존 버전을 수정하지 않고 `V8` 이상의 신규 마이그레이션만 추가해야 합니다.
- `citext`, `vector` 확장 설치에는 데이터베이스 권한이 필요할 수 있습니다.

## 첨부 파일

- `all-my-trips-postgresql-db-design-v1.2.0.pdf`: DB 설계, 실행 절차, 완료 보고서를 합친 배포용 문서
- `all-my-trips-postgresql-db-files-v1.2.0.zip`: SQL 원본과 Markdown 문서 묶음
- `SHA256SUMS.txt`: 첨부 파일 무결성 검증값

## Publish 전 확인

- PostgreSQL 16 환경에서 확장 설치, `V1`~`V7`, seed, validation을 실제로 순서대로 실행
- validation 결과의 기대값과 실제값 비교
- PR 병합 후 Release 대상 커밋을 `main`으로 변경
- Draft 상태와 첨부 파일 체크섬 최종 확인
