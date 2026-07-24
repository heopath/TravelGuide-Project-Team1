# All My Trips Database

MySQL 8.0, InnoDB, `utf8mb4`를 사용하는 로컬 개발 DB 구성 안내서다. 운영 마이그레이션과 합성 seed는 분리한다.

## 1. 데이터베이스 생성

저장소 루트에서 초기화 DDL을 먼저 실행한다.

```bash
mysql -u root -p < database/init/create_database.sql
```

파일에 포함된 쿼리는 다음과 같다.

```sql
CREATE DATABASE IF NOT EXISTS all_my_trips
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

Flyway 또는 MySQL 접속 대상 DB를 `all_my_trips`로 지정한다. V1~V7 파일에는 `CREATE DATABASE`와 `USE`를 넣지 않았다.

## 2. 폴더 구조

```text
database/
├── init/
│   └── create_database.sql
├── migration/
│   ├── V1__member_and_place.sql
│   ├── V2__trip_and_itinerary.sql
│   ├── V3__ai_and_recommendation.sql
│   ├── V4__travel_record.sql
│   ├── V5__travel_record_social.sql
│   ├── V6__admin_operation.sql
│   └── V7__ticket_reservation.sql
├── seed/
│   ├── local_seed_users.sql
│   ├── local_seed_places.sql
│   ├── local_seed_trips.sql
│   ├── local_seed_ai.sql
│   ├── local_seed_records.sql
│   └── local_seed_tickets.sql
├── validation/
│   └── validate_seed_data.sql
├── DB_WORK_COMPLETION_REPORT.md
└── README.md
```

## 3. 실행 순서

마이그레이션은 반드시 V1부터 V7까지 실행한다. Flyway를 사용하지 않을 때는 다음 순서를 따른다.

```bash
mysql --default-character-set=utf8mb4 -u root -p all_my_trips < database/migration/V1__member_and_place.sql
mysql --default-character-set=utf8mb4 -u root -p all_my_trips < database/migration/V2__trip_and_itinerary.sql
mysql --default-character-set=utf8mb4 -u root -p all_my_trips < database/migration/V3__ai_and_recommendation.sql
mysql --default-character-set=utf8mb4 -u root -p all_my_trips < database/migration/V4__travel_record.sql
mysql --default-character-set=utf8mb4 -u root -p all_my_trips < database/migration/V5__travel_record_social.sql
mysql --default-character-set=utf8mb4 -u root -p all_my_trips < database/migration/V6__admin_operation.sql
mysql --default-character-set=utf8mb4 -u root -p all_my_trips < database/migration/V7__ticket_reservation.sql
```

1차 seed는 다음 순서다. 티켓은 2주차 데이터이므로 마지막 파일로 분리했다.

```bash
mysql --default-character-set=utf8mb4 -u root -p < database/seed/local_seed_users.sql
mysql --default-character-set=utf8mb4 -u root -p < database/seed/local_seed_places.sql
mysql --default-character-set=utf8mb4 -u root -p < database/seed/local_seed_trips.sql
mysql --default-character-set=utf8mb4 -u root -p < database/seed/local_seed_ai.sql
mysql --default-character-set=utf8mb4 -u root -p < database/seed/local_seed_records.sql
mysql --default-character-set=utf8mb4 -u root -p < database/seed/local_seed_tickets.sql
mysql --default-character-set=utf8mb4 -u root -p < database/validation/validate_seed_data.sql
```

## 4. 개발 전용 계정

`local-user1@example.invalid`부터 `local-user8@example.invalid`은 USER, 9~10은 ADMIN이다. 7번은 SUSPENDED, 8번은 WITHDRAWN이며 나머지는 ACTIVE다.

현재 `password_hash`에는 `REPLACE_WITH_TEAM_BCRYPT_HASH`가 들어 있다. 팀장이 로컬에서 제공한 BCrypt 해시로 이 문자열을 교체하기 전에는 로그인 테스트를 진행하지 않는다. 온라인 해시 생성 사이트를 사용하지 않는다.

## 5. 외부 데이터 원칙

- 모든 계정·주소·전화·결제키·JSON은 합성 데이터다.
- 이미지는 `picsum.photos` 외부 URL을 사용하며 파일을 저장소에 포함하지 않는다.
- 숙소는 `places.category='ACCOMMODATION'`으로 저장하고 `website_url`로 외부 예약 페이지를 표현한다.
- 항공권 전용 테이블과 항공 seed는 만들지 않는다. 애플리케이션이 허용된 외부 검색 링크를 생성한다.
- 티켓·결제·발권은 실제 공급사나 PG와 연결되지 않은 Mock 데이터다.

## 6. 검증 예상 결과

`validate_seed_data.sql`의 위반 조회는 모두 0행이어야 한다. 건수 표의 실제 값은 기대값 이상이어야 한다. BCrypt 교체 전 마지막 쿼리는 10행을 반환하며, 교체 후에는 0행이어야 한다.

## 7. 알려진 제한사항과 확인 필요 사항

- 업무지시서는 완료 여행 4건, 여행당 기록 1건과 여행 기록 8~10건을 동시에 요구한다. 현재 seed는 무결성을 우선해 기록 4건을 생성한다. 완료 여행 또는 전체 여행 수를 늘릴지 팀장 확인이 필요하다.
- 외부 이미지 URL은 제공 서비스 상태에 따라 표시되지 않을 수 있다.
- 행 간 규칙인 여행 소유자 일치, 완료 여행만 기록 가능, 답글의 동일 기록 소속은 MySQL CHECK만으로 강제할 수 없으므로 서비스와 검증 SQL에서 확인한다.
- seed 파일은 빈 로컬 DB에서 한 번 실행하는 용도이며 반복 실행용 UPSERT가 아니다.

## 8. 업무 완료 보고

업무지시서 양식에 따른 진행 상태와 검증 결과는 [DB_WORK_COMPLETION_REPORT.md](DB_WORK_COMPLETION_REPORT.md)에서 관리한다.
