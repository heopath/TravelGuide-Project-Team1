# DB 업무 완료 보고

> 업무지시서의 완료 보고 양식을 기준으로 작성했다. 실제 MySQL 실행 결과와 BCrypt 해시는 아직 확정되지 않았으므로 해당 항목은 검증 대기로 표시한다.

## 1. 작성한 마이그레이션

- `database/init/create_database.sql`: `all_my_trips` 데이터베이스와 `utf8mb4` 문자셋 생성
- `V1__member_and_place.sql`: 회원·선호·장소·즐겨찾기
- `V2__trip_and_itinerary.sql`: 여행·일차·일정 항목
- `V3__ai_and_recommendation.sql`: AI 요청·추천 행동
- `V4__travel_record.sql`: 여행 기록·기록 이미지
- `V5__travel_record_social.sql`: 댓글·좋아요·공유·신고
- `V6__admin_operation.sql`: 장소 동기화·관리자 감사
- `V7__ticket_reservation.sql`: 관광 티켓·재고·예약·모의 결제·발권·검표

## 2. 작성한 seed 파일

- `local_seed_users.sql`
- `local_seed_places.sql`
- `local_seed_trips.sql`
- `local_seed_ai.sql`
- `local_seed_records.sql`
- `local_seed_tickets.sql`

실제 계정이나 개인정보를 사용하지 않으며, 사용자 비밀번호에는 `REPLACE_WITH_TEAM_BCRYPT_HASH` 교체용 값이 들어 있다.

## 3. 추가한 테이블

여행 기록 및 소셜 기능을 위해 다음 6개 테이블을 추가했다.

- `travel_records`
- `travel_record_images`
- `travel_record_comments`
- `travel_record_likes`
- `travel_record_shares`
- `travel_record_reports`

기존 23개 테이블과 합쳐 전체 스키마는 29개 테이블이다.

## 4. 데이터 건수

| 데이터 | 작성 기준 건수 |
| --- | ---: |
| 회원 | 10 |
| 여행 스타일 | 5 |
| 사용자 선호 | 30 |
| 장소 | 100 |
| 장소 이미지 | 200 |
| 장소 스타일 | 200 |
| 즐겨찾기 | 40 |
| 여행 | 15 |
| 여행 일차 | 40 |
| 일정 항목 | 160 |
| AI 요청 | 20 |
| 추천 이벤트 | 120 |
| 여행 기록 | 4 |
| 여행 기록 이미지 | 16 |
| 댓글·답글 | 24 |
| 좋아요 | 32 |
| 공유 | 12 |
| 신고 | 6 |
| 티켓 상품 | 20 |
| 예약 | 15 |
| 예약 항목 | 20 |
| 결제 | 15 |
| 검표 로그 | 20 |

발급 티켓은 `PAID` 결제에 연결된 예약 항목의 구매 수량만큼 생성한다.

## 5. MySQL 실행 결과

- 상태: **사용자 로컬 MySQL 실행 검증 대기**
- 정적 검증: V1~V7에서 29개 테이블 생성 순서와 FK 부모 선행 관계 확인 완료
- 미실행 사유: 작성 환경의 임시 MySQL 8.4 초기화 과정에서 로컬 바이너리가 SIGSEGV로 종료됨
- 완료 조건: 빈 MySQL 8.0+에서 init → V1~V7 → seed → validation 순서로 오류 없이 실행

## 6. 검증 SQL 결과

- 파일: `database/validation/validate_seed_data.sql`
- 정적 검토: 완료
- 실제 조회 결과: **사용자 로컬 MySQL 실행 대기**
- 예상 결과: 무결성 위반 조회 모두 0행
- BCrypt 확인: 교체 전 10행, 팀 제공 해시로 교체 후 0행

## 7. 팀장 확인 필요 사항

1. 업무지시서는 `COMPLETED` 여행 4건, 여행당 기록 1건, 여행 기록 8~10건을 동시에 요구한다.
2. 세 조건은 동시에 만족할 수 없으므로 현재 seed는 완료 여행 4건에 기록 4건만 생성한다.
3. 여행 기록을 8~10건으로 늘려야 한다면 완료 여행 수를 먼저 8~10건으로 늘릴지 확인이 필요하다.
4. 로그인 테스트 전에 개발 계정에 사용할 BCrypt 해시 제공이 필요하다.

## 8. 알려진 제한사항

- 여행 기록은 업무지시서 권장 8~10건이 아니라 무결성을 지킬 수 있는 4건만 생성한다.
- seed는 빈 로컬 DB에서 한 번 실행하는 구조이며 반복 실행 멱등성을 제공하지 않는다.
- 외부 이미지 URL은 외부 서비스 상태에 따라 표시되지 않을 수 있다.
- 여행 소유자 일치, 완료 여행만 기록 가능, 답글의 동일 기록 소속은 다른 행을 비교해야 하므로 서비스 계층과 검증 SQL에서 추가로 보장한다.
- 항공권과 숙소 자체 예약 테이블은 생성하지 않는다. 숙소는 외부 링크를 사용한다.
- 티켓·결제·발권은 실제 공급사나 PG와 연동하지 않는 Mock 구조다.
