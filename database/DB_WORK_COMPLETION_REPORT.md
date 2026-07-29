# DB 업무 완료 보고

> 업무지시서의 완료 보고 양식을 유지하면서 PostgreSQL 전환과 페이지 명세 재검토 결과를 반영했다. 실제 로컬 PostgreSQL 실행 결과와 BCrypt 해시는 팀 환경에서 최종 확인한다.

## 1. 작성한 PostgreSQL 마이그레이션

- `database/init/create_database.sql`: `all_my_trips` DB 생성, `database/init/create_extensions.sql`: 관리자 권한 확장 설치
- `V1__member_and_place.sql`: 회원·선호·장소·즐겨찾기
- `V2__trip_and_itinerary.sql`: 여행·일차·일정·공유 링크
- `V3__ai_and_recommendation.sql`: AI 대화·요청·정규화 추천 결과·행동
- `V4__travel_record.sql`: 여행 기록·이미지
- `V5__travel_record_social.sql`: 댓글·좋아요·공유·신고
- `V6__admin_operation.sql`: 테마 여행·장소 동기화·관리자 감사
- `V7__ticket_reservation.sql`: 티켓 상품·옵션·시간대 재고·예약·모의 결제·발권·검표

## 2. DBMS 전환 내용

- MySQL `AUTO_INCREMENT` → PostgreSQL identity
- `DATETIME(6)` → `TIMESTAMPTZ`
- `JSON` → `JSONB`, IP 문자열 → `INET`
- `ON UPDATE CURRENT_TIMESTAMP` → 공통 `set_updated_at()` trigger
- MySQL 조건부 운영 규칙 → PostgreSQL 부분 유일 인덱스
- MySQL JDBC/Flyway 의존성 제거, PostgreSQL 드라이버만 유지
- Docker Compose와 local profile의 DB명·계정·포트를 `all_my_trips`로 통일

## 3. 페이지 명세 검토 결과

추가한 10개 비즈니스 테이블:

- `trip_share_links`
- `ai_chat_sessions`, `ai_chat_messages`
- `recommendation_sessions`, `recommendation_results`
- `travel_themes`, `travel_theme_styles`, `travel_theme_places`
- `ticket_product_options`, `ticket_time_slots`

기존 29개와 합쳐 전체 비즈니스 스키마는 39개 테이블이다. RAG의 `vector_store`는 Spring AI가 관리하는 인프라 테이블이므로 이 수에 포함하지 않는다.

추가하지 않은 구조:

- 항공·숙소 자체 예약: 외부 검색/링크
- 날씨·교통 원본: 외부 API와 Redis 단기 캐시
- 예약 대기열: Redis TTL·순번·입장 토큰
- 결제수단: Mock 결제이며 민감 원문 미저장

## 4. seed·검증

모든 seed와 validation을 PostgreSQL 문법으로 변환했다. 신규 테이블 seed는 테마 4건·구성 장소 24건, AI 대화 5개·메시지 10건, 추천 세션 20건·결과 200건, 티켓 옵션/시간대 각 60건을 포함한다.

검증 SQL은 다음을 확인한다.

- 완료 여행·여행 기록 소유자와 상태
- 여행 기간과 일차 날짜
- 대표 이미지·댓글 부모·추천 사용자·AI 대화 세션 일치
- 티켓 시간대와 예약 스냅샷·재고·발급 수량
- BCrypt placeholder 잔존 여부

## 5. 실행 결과

- 정적 검사: PostgreSQL 전용 문법과 39개 테이블 FK 생성 순서 확인
- 실제 실행: 로컬 Docker PostgreSQL에서 `init → V1~V7 → seed → validation` 순서로 확인 필요
- 완료 기준: 모든 파일이 `ON_ERROR_STOP=1`로 오류 없이 실행되고 위반 조회가 0행

## 6. 팀장 확인 필요 사항

1. `COMPLETED` 여행 4건과 여행당 기록 1건 규칙 때문에 여행 기록 seed는 4건을 유지한다.
2. 로그인 테스트 전에 팀 개발 계정용 BCrypt 해시를 제공해야 한다.
3. 추천·AI·티켓 페이지 API 계약이 확정되면 신규 테이블의 DTO 매핑을 함께 검토한다.

## 7. 알려진 제한사항

- seed는 빈 로컬 DB에서 한 번 실행하며 반복 실행 UPSERT를 제공하지 않는다.
- 여행 소유자 일치 등 행 간 규칙은 Service와 validation SQL에서 함께 보장한다.
- 티켓·결제·발권은 실제 공급사나 PG와 연동하지 않는 Mock 구조다.
