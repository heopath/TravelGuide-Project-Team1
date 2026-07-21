# TripPilot 데이터베이스 설계서

## 1. 설계 기준

- 대상 DBMS: MySQL 8.0 이상, InnoDB, `utf8mb4`
- 식별자는 숫자형 대리 키를 기본으로 하고, 외부 노출 예약에는 별도 예약번호를 사용한다.
- 금액은 `DECIMAL(15,2)`와 ISO 4217 통화 코드를 함께 저장한다.
- 회원과 여행은 `deleted_at`을 이용해 소프트 삭제하며, 일정의 하위 데이터는 부모 삭제 시 함께 삭제한다.
- 외부 장소 API 값은 `external_provider`와 `external_place_id`로 멱등하게 동기화한다.
- Redis의 대기열/분산 락 데이터와 Vector DB의 임베딩 본문은 수명이 다른 운영 데이터이므로 MySQL 스키마에 포함하지 않는다. MySQL은 재고 원장과 AI 요청 이력을 영속 저장한다.

## 2. 도메인별 연관 테이블

| 연관관계가 있는 테이블들(도메인) | 설명 |
| --- | --- |
| `users` → `user_preferences` ← `travel_styles` (회원·선호) | 회원 계정과 명시적/추론된 여행 스타일 점수를 관리한다. 한 회원은 스타일마다 하나의 선호 점수만 가진다. |
| `places` → `place_images`, `places` → `place_travel_styles` ← `travel_styles`, `users` + `places` → `favorites` (장소·스타일·즐겨찾기) | 외부 관광 데이터와 서비스 자체 장소를 통합하고, 장소 이미지, 스타일별 적합도 및 회원별 즐겨찾기를 관리한다. |
| `users` → `trips` → `trip_days` → `itinerary_items`, `trips` + `travel_styles` → `trip_travel_styles` (여행 일정) | 여행 입력 조건, 날짜별 구획, 순서가 있는 활동을 계층화한다. 일정 항목은 장소를 선택적으로 참조해 장소가 삭제되어도 제목 스냅샷을 보존한다. |
| `users` → `ai_generation_requests` → `trips` (AI) | 일정 생성, 동선 최적화, 챗봇 요청의 입력·결과·모델·토큰·상태를 감사 가능하게 기록한다. 실패하거나 아직 여행이 만들어지지 않은 요청은 `trip_id`가 없을 수 있다. |
| `users` + `places` → `recommendation_events`, `recommendation_events` → `trips`, `ai_generation_requests` (추천 행동) | 추천 노출·클릭·즐겨찾기·일정 추가·제거·숨김 행동을 기록해 추천 성과를 측정하고 `user_preferences`의 추론 점수 갱신 근거로 사용한다. |
| `users` → `place_sync_jobs` → `place_sync_errors`, `users` → `admin_audit_logs` (관리자·장소 수집 운영) | 관리자 또는 스케줄러가 실행한 장소 수집 작업과 레코드별 실패를 기록하고, 관리자·시스템의 데이터 변경 이력을 감사 로그로 보존한다. |
| `places` → `ticket_products` → `ticket_inventory`, `users` → `reservations` → `reservation_items` ← `ticket_products`, `reservations` → `payments`, `reservation_items` → `issued_tickets` → `ticket_validation_logs` (예약·결제·발권) | 장소별 모의 티켓, 단일 집계 재고, 주문·결제, 개별 전자 티켓 발급과 검표 이력을 관리한다. 재고와 티켓 상태는 원자적으로 변경해 초과 판매와 중복 사용을 방지한다. |

## 2-1. 구현 단계별 테이블 구성

현재 DDL은 1차 MVP부터 2차 예약 확장 및 3차 AI 고도화까지 고려한 통합 스키마 초안이다. 아래 구분은 개발·배포 우선순위를 나타내며, DDL에서 테이블을 제거한다는 의미는 아니다.

| 구현 단계 | 대상 테이블 | 적용 기능 |
| --- | --- | --- |
| 1차 MVP — 회원·선호 | `users`, `travel_styles`, `user_preferences` | 회원가입·로그인, 권한, 명시적·추론형 여행 스타일 선호 관리 |
| 1차 MVP — 장소·추천 기반 | `places`, `place_images`, `place_travel_styles`, `favorites` | 여행지 탐색, 장소 상세, 즐겨찾기, 장소별 여행 스타일 적합도 |
| 1차 MVP — 여행 일정 | `trips`, `trip_travel_styles`, `trip_days`, `itinerary_items` | AI·수동 여행 생성, 날짜별 일정 편집, 장소 순서와 메모 관리 |
| 1차 MVP — AI·개인화 기반 | `ai_generation_requests`, `recommendation_events` | AI 일정 생성 이력, 추천 노출·클릭·즐겨찾기·일정 추가 행동 수집 |
| 1차 관리자 운영 | `place_sync_jobs`, `place_sync_errors`, `admin_audit_logs` | 장소 데이터 수집 작업, 실패 재처리, 운영자 변경 이력 관리 |
| 2차 예약·동시성 확장 | `ticket_products`, `ticket_inventory`, `reservations`, `reservation_items`, `payments`, `issued_tickets`, `ticket_validation_logs` | 모의 티켓 판매, 재고 동시성 제어, 예약·결제, 전자 발권과 중복 검표 방지 |
| 3차 AI 고도화 | 1차의 `user_preferences`, `place_travel_styles`, `recommendation_events`, `ai_generation_requests` 재사용 | 행동 기반 선호 추론, 개인화 추천, 동선 최적화와 RAG 응답 품질 개선 |

### 단계별 적용 원칙

- 1차 핵심 MVP는 관리자 테이블을 제외한 13개 테이블을 사용한다.
- 외부 장소 데이터를 자동 수집하거나 운영자가 직접 검수하는 시점에는 관리자 운영 3개 테이블을 1차 범위에 함께 적용한다.
- 2차의 예약·결제·발권 7개 테이블은 1차 기능과 분리된 마이그레이션으로 배포할 수 있다.
- 3차는 현재 별도의 MySQL 전용 테이블을 요구하지 않는다. 1차부터 누적한 선호·장소 스타일·추천 행동·AI 요청 데이터를 재사용한다.
- RAG 문서 본문과 임베딩 벡터는 Vector DB에 저장하며, MySQL에는 원본 장소와 추천·AI 사용 이력을 유지한다.
- 실제 개발 범위가 변경되면 이 단계 구분과 마이그레이션 순서를 함께 갱신한다.

## 3. 핵심 관계 및 무결성

- 회원 이메일·닉네임, 여행 스타일 코드, 외부 제공자별 장소 ID, 즐겨찾기 `(user_id, place_id)`는 중복될 수 없다.
- 장소별 여행 스타일은 `(place_id, travel_style_id)` 조합당 하나만 존재하며 적합도는 0~100 범위다.
- 추천 행동은 회원과 장소를 필수로 참조하고, 관련 여행과 AI 요청은 선택적으로 참조한다.
- 여행 종료일은 시작일보다 빠를 수 없고, 여행별 일차 번호 및 날짜는 각각 유일하다.
- 일차 안의 `sort_order`는 유일하며 순서 변경 시 관련 행을 한 트랜잭션에서 갱신한다.
- 장소 동기화 작업의 처리·생성·수정·실패 건수는 CHECK 제약 범위 안에서 누적하며, 레코드별 실패는 해당 작업의 하위 오류로 저장한다.
- `admin_audit_logs`는 수정·삭제하지 않는 추가 전용 감사 로그로 운영하고, 관리자 탈퇴 후에도 로그 행은 유지한다.
- 티켓 재고는 `reserved_quantity <= total_quantity`를 항상 만족해야 한다. `UPDATE ... WHERE version = ? AND reserved_quantity + ? <= total_quantity` 형태의 조건부 갱신을 권장한다.
- 결제 확정 후 예약 수량만큼 `issued_tickets`를 개별 생성하며 티켓 번호와 검증 토큰 해시는 각각 유일해야 한다.
- 검표 성공은 `issued_tickets.status`를 `ISSUED`에서 `USED`로 조건부 변경한 요청 하나에만 허용하고 모든 시도를 `ticket_validation_logs`에 기록한다.
- 예약 상품명과 단가는 구매 당시 스냅샷이다. 이후 상품이 바뀌어도 기존 주문 내용은 유지된다.
- CHECK 제약에 사용된 상태/유형 값은 애플리케이션 enum과 함께 변경해야 한다.

## 4. 전체 테이블 컬럼 사전

### 4.1 `users` — 회원 계정

| 컬럼이름 | 설명 |
| --- | --- |
| `user_id` | 회원 PK |
| `email` | 로그인 이메일, 유일값 |
| `password_hash` | 단방향 인코딩된 비밀번호 |
| `nickname` | 화면 표시 닉네임, 유일값 |
| `role` | 권한: `USER`, `ADMIN` |
| `status` | 계정 상태: `ACTIVE`, `SUSPENDED`, `WITHDRAWN` |
| `last_login_at` | 최근 로그인 일시 |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |
| `deleted_at` | 소프트 삭제 일시 |

### 4.2 `travel_styles` — 여행 스타일 코드

| 컬럼이름 | 설명 |
| --- | --- |
| `travel_style_id` | 여행 스타일 PK |
| `code` | 애플리케이션에서 사용하는 고정 코드, 유일값 |
| `name` | 표시 이름, 유일값 |
| `description` | 스타일 설명 |
| `is_active` | 선택 가능 여부 |
| `sort_order` | 화면 표시 순서 |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.3 `user_preferences` — 회원별 선호

| 컬럼이름 | 설명 |
| --- | --- |
| `user_preference_id` | 회원 선호 PK |
| `user_id` | 선호를 가진 회원 FK |
| `travel_style_id` | 대상 여행 스타일 FK |
| `preference_score` | 0~100 선호 점수 |
| `source` | 수집 방식: 명시 입력 `EXPLICIT`, 행동 추론 `INFERRED` |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.4 `places` — 여행 장소

| 컬럼이름 | 설명 |
| --- | --- |
| `place_id` | 장소 PK |
| `external_provider` | 외부 데이터 제공자명 |
| `external_place_id` | 제공자 시스템의 장소 ID |
| `category` | `ATTRACTION`, `RESTAURANT`, `CAFE`, `ACCOMMODATION`, `FESTIVAL`, `ACTIVITY`, `TRANSPORT` 중 하나 |
| `name` | 장소명 |
| `country_code` | ISO 3166-1 alpha-2 국가 코드 |
| `region` | 시·도 등 광역 지역 |
| `city` | 시·군·구 등 세부 지역 |
| `address` | 도로명 또는 상세 주소 |
| `latitude` | 위도(-90~90) |
| `longitude` | 경도(-180~180) |
| `description` | 장소 상세 설명 |
| `phone` | 연락처 |
| `website_url` | 공식 웹사이트 URL |
| `average_rating` | 0~5 평균 평점 |
| `is_active` | 서비스 노출 여부 |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.5 `place_images` — 장소 이미지

| 컬럼이름 | 설명 |
| --- | --- |
| `place_image_id` | 장소 이미지 PK |
| `place_id` | 이미지가 속한 장소 FK |
| `image_url` | 이미지 URL |
| `alt_text` | 접근성을 위한 대체 텍스트 |
| `sort_order` | 장소 내 표시 순서 |
| `is_primary` | 대표 이미지 여부 |
| `created_at` | 생성 일시 |

### 4.6 `favorites` — 즐겨찾기

| 컬럼이름 | 설명 |
| --- | --- |
| `favorite_id` | 즐겨찾기 PK |
| `user_id` | 저장한 회원 FK |
| `place_id` | 저장한 장소 FK |
| `memo` | 회원이 남긴 즐겨찾기 메모 |
| `created_at` | 생성 일시 |

### 4.7 `trips` — 여행 계획

| 컬럼이름 | 설명 |
| --- | --- |
| `trip_id` | 여행 PK |
| `user_id` | 여행 소유 회원 FK |
| `title` | 여행 제목 |
| `destination_name` | 사용자가 입력한 목적지명 |
| `start_date` | 여행 시작일 |
| `end_date` | 여행 종료일 |
| `companion_type` | `SOLO`, `FRIENDS`, `COUPLE`, `FAMILY`, `GROUP`, `OTHER` 중 하나 |
| `companion_count` | 본인을 포함한 여행 인원 |
| `purpose` | 여행 목적 |
| `budget_amount` | 전체 예상 예산 |
| `currency_code` | ISO 4217 통화 코드 |
| `transport_preference` | 선호 이동 수단 |
| `food_preference` | 선호 음식 |
| `pace` | 여행 강도: `RELAXED`, `NORMAL`, `PACKED` |
| `accommodation_style` | 선호 숙박 형태 |
| `status` | `DRAFT`, `CONFIRMED`, `COMPLETED`, `CANCELLED` 중 하나 |
| `source` | 최초 생성 방식: `MANUAL`, `AI` |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |
| `deleted_at` | 소프트 삭제 일시 |

### 4.8 `trip_travel_styles` — 여행별 스타일

| 컬럼이름 | 설명 |
| --- | --- |
| `trip_id` | 여행 FK이자 복합 PK 구성 컬럼 |
| `travel_style_id` | 여행 스타일 FK이자 복합 PK 구성 컬럼 |
| `priority` | 작은 값이 우선인 스타일 우선순위 |
| `created_at` | 생성 일시 |

### 4.9 `trip_days` — 여행 일차

| 컬럼이름 | 설명 |
| --- | --- |
| `trip_day_id` | 여행 일차 PK |
| `trip_id` | 상위 여행 FK |
| `day_number` | 1부터 시작하는 일차 번호 |
| `trip_date` | 해당 일차의 실제 날짜 |
| `title` | 선택적인 일차 제목 |
| `memo` | 일차 단위 메모 |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.10 `itinerary_items` — 일정 항목

| 컬럼이름 | 설명 |
| --- | --- |
| `itinerary_item_id` | 일정 항목 PK |
| `trip_day_id` | 상위 여행 일차 FK |
| `place_id` | 연결된 장소 FK, 직접 메모 항목이면 NULL 가능 |
| `item_type` | `PLACE`, `MEAL`, `ACCOMMODATION`, `TRANSPORT`, `ACTIVITY`, `NOTE` 중 하나 |
| `title` | 항목 제목 또는 당시 장소명 스냅샷 |
| `start_time` | 예정 시작 시각 |
| `end_time` | 예정 종료 시각 |
| `sort_order` | 일차 안에서의 표시 순서 |
| `memo` | 사용자 메모 |
| `estimated_cost` | 항목별 예상 비용 |
| `currency_code` | ISO 4217 통화 코드 |
| `source` | 생성 방식: `MANUAL`, `AI` |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.11 `ai_generation_requests` — AI 요청 이력

| 컬럼이름 | 설명 |
| --- | --- |
| `ai_generation_request_id` | AI 요청 PK |
| `user_id` | 요청 회원 FK |
| `trip_id` | 생성 또는 최적화 대상 여행 FK |
| `request_type` | `CREATE_ITINERARY`, `OPTIMIZE_ROUTE`, `CHAT` 중 하나 |
| `provider` | Ollama, Gemini 등 제공자명 |
| `model_name` | 호출한 모델명 |
| `prompt_version` | 프롬프트 템플릿 버전 |
| `input_payload` | 구조화된 입력 JSON |
| `output_payload` | 구조화된 모델 응답 JSON |
| `status` | `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED` 중 하나 |
| `error_message` | 실패 사유 |
| `input_tokens` | 입력 토큰 수 |
| `output_tokens` | 출력 토큰 수 |
| `requested_at` | 요청 일시 |
| `completed_at` | 처리 완료 일시 |

### 4.12 `ticket_products` — 티켓 상품

| 컬럼이름 | 설명 |
| --- | --- |
| `ticket_product_id` | 티켓 상품 PK |
| `place_id` | 상품 관련 장소 FK |
| `name` | 상품명 |
| `description` | 상품 설명 |
| `sale_start_at` | 판매 시작 일시 |
| `sale_end_at` | 판매 종료 일시 |
| `usage_start_date` | 사용 가능 시작일 |
| `usage_end_date` | 사용 가능 종료일 |
| `unit_price` | 단가 |
| `currency_code` | ISO 4217 통화 코드 |
| `max_quantity_per_user` | 회원 1명당 최대 구매 수량 |
| `status` | `DRAFT`, `ON_SALE`, `SOLD_OUT`, `ENDED`, `CANCELLED` 중 하나 |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.13 `ticket_inventory` — 티켓 재고

| 컬럼이름 | 설명 |
| --- | --- |
| `ticket_product_id` | 티켓 상품 FK이자 PK |
| `total_quantity` | 총 판매 가능 수량 |
| `reserved_quantity` | 임시 확보 또는 판매 완료된 수량 |
| `version` | 낙관적 락 버전 |
| `updated_at` | 최종 재고 변경 일시 |

### 4.14 `reservations` — 예약 주문

| 컬럼이름 | 설명 |
| --- | --- |
| `reservation_id` | 예약 PK |
| `reservation_number` | 외부 노출용 유일 예약번호 |
| `user_id` | 예약 회원 FK |
| `status` | `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `USED` 중 하나 |
| `total_amount` | 예약 총액 |
| `currency_code` | ISO 4217 통화 코드 |
| `expires_at` | 결제 대기 재고 확보 만료 일시 |
| `confirmed_at` | 예약 확정 일시 |
| `cancelled_at` | 예약 취소 일시 |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.15 `reservation_items` — 예약 상세

| 컬럼이름 | 설명 |
| --- | --- |
| `reservation_item_id` | 예약 상세 PK |
| `reservation_id` | 상위 예약 FK |
| `ticket_product_id` | 구매 티켓 상품 FK |
| `product_name` | 구매 당시 상품명 스냅샷 |
| `usage_date` | 선택한 사용일 |
| `quantity` | 예약 수량 |
| `unit_price` | 구매 당시 단가 스냅샷 |
| `line_amount` | 단가 × 수량인 상세 금액 |
| `created_at` | 생성 일시 |

### 4.16 `payments` — 결제 시도 및 결과

| 컬럼이름 | 설명 |
| --- | --- |
| `payment_id` | 결제 PK |
| `reservation_id` | 결제 대상 예약 FK |
| `provider` | 결제 서비스 제공자명 |
| `provider_payment_key` | 제공자가 발급한 결제 식별자 |
| `method` | `CARD`, `TRANSFER`, `VIRTUAL_ACCOUNT`, `EASY_PAY` 중 하나 |
| `status` | `READY`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED` 중 하나 |
| `amount` | 결제 금액 |
| `currency_code` | ISO 4217 통화 코드 |
| `failure_code` | 제공자 실패 코드 |
| `failure_message` | 제공자 실패 메시지 |
| `requested_at` | 결제 요청 일시 |
| `approved_at` | 결제 승인 일시 |
| `cancelled_at` | 취소 또는 환불 일시 |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.17 `place_travel_styles` — 장소별 여행 스타일 적합도

| 컬럼이름 | 설명 |
| --- | --- |
| `place_travel_style_id` | 장소-여행 스타일 연결 PK |
| `place_id` | 태그가 적용된 장소 FK |
| `travel_style_id` | 장소와 연결된 여행 스타일 FK |
| `relevance_score` | 해당 장소와 스타일의 0~100 적합도 |
| `source` | 태그 생성 방식: `MANUAL`, `AI`, `BEHAVIOR` |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.18 `recommendation_events` — 추천 사용자 행동 이력

| 컬럼이름 | 설명 |
| --- | --- |
| `recommendation_event_id` | 추천 행동 이벤트 PK |
| `user_id` | 행동한 회원 FK |
| `place_id` | 추천된 장소 FK |
| `trip_id` | 행동과 관련된 여행 FK, 없으면 NULL |
| `ai_generation_request_id` | 추천을 생성한 AI 요청 FK, 없으면 NULL |
| `event_type` | `IMPRESSION`, `CLICK`, `FAVORITE`, `ADD_TO_TRIP`, `REMOVE_FROM_TRIP`, `DISMISS` 중 하나 |
| `session_id` | 한 추천 결과 묶음을 추적하는 세션 ID |
| `metadata` | 노출 순위, 추천 점수, 화면 위치 등 추가 문맥 JSON |
| `occurred_at` | 행동 발생 일시 |

### 4.19 `place_sync_jobs` — 장소 데이터 동기화 작업

| 컬럼이름 | 설명 |
| --- | --- |
| `place_sync_job_id` | 장소 동기화 작업 PK |
| `requested_by` | 작업을 실행한 관리자 FK, 스케줄 작업이면 NULL |
| `provider` | 외부 장소 데이터 제공자명 |
| `job_type` | `FULL`, `INCREMENTAL`, `MANUAL` 중 하나 |
| `status` | `PENDING`, `RUNNING`, `SUCCEEDED`, `PARTIAL_FAILED`, `FAILED` 중 하나 |
| `requested_count` | 조회하거나 발견한 원본 레코드 수 |
| `processed_count` | 처리 완료한 원본 레코드 수 |
| `created_count` | 새로 생성한 장소 수 |
| `updated_count` | 갱신한 장소 수 |
| `failed_count` | 처리에 실패한 레코드 수 |
| `error_summary` | 작업 전체 오류 요약 |
| `started_at` | 작업 시작 일시 |
| `completed_at` | 작업 완료 일시 |
| `created_at` | 작업 요청 생성 일시 |

### 4.20 `place_sync_errors` — 장소 동기화 오류

| 컬럼이름 | 설명 |
| --- | --- |
| `place_sync_error_id` | 장소 동기화 오류 PK |
| `place_sync_job_id` | 오류가 발생한 동기화 작업 FK |
| `external_place_id` | 실패한 외부 장소 식별자 |
| `error_code` | 프로그램에서 판별 가능한 오류 코드 |
| `error_message` | 오류 설명 |
| `raw_payload` | 진단과 재처리를 위한 실패 원본 JSON |
| `retry_status` | `PENDING`, `RETRIED`, `RESOLVED`, `IGNORED` 중 하나 |
| `retry_count` | 재처리 시도 횟수 |
| `last_retried_at` | 최근 재처리 시도 일시 |
| `resolved_at` | 오류 해결 일시 |
| `created_at` | 생성 일시 |

### 4.21 `admin_audit_logs` — 관리자 감사 로그

| 컬럼이름 | 설명 |
| --- | --- |
| `admin_audit_log_id` | 관리자 감사 로그 PK |
| `admin_user_id` | 작업한 관리자 FK, 시스템 작업이면 NULL |
| `action_type` | 생성·수정·비활성화·동기화 등 관리자 작업 유형 |
| `target_type` | 변경한 엔티티 또는 리소스 유형 |
| `target_id` | 변경한 대상 식별자 |
| `before_data` | 변경 전 데이터 JSON 스냅샷 |
| `after_data` | 변경 후 데이터 JSON 스냅샷 |
| `request_id` | 요청 연계 식별자 |
| `ip_address` | 관리자 IPv4 또는 IPv6 주소 |
| `user_agent` | 관리자 클라이언트 User-Agent |
| `occurred_at` | 작업 발생 일시 |

### 4.22 `issued_tickets` — 발급된 모의 전자 티켓

| 컬럼이름 | 설명 |
| --- | --- |
| `issued_ticket_id` | 발급 티켓 PK |
| `reservation_item_id` | 발권 대상 예약 상세 FK |
| `ticket_number` | 사용자에게 노출하는 유일 티켓 번호 |
| `verification_token_hash` | QR 검증 토큰 원문의 SHA-256 해시 |
| `issue_method` | `MOBILE`, `EMAIL`, `PRINT`, `ONSITE` 중 하나 |
| `status` | `ISSUED`, `USED`, `CANCELLED`, `EXPIRED`, `REPLACED` 중 하나 |
| `valid_from` | 사용 가능 시작 일시 |
| `valid_until` | 사용 가능 종료 일시 |
| `issued_at` | 발급 일시 |
| `used_at` | 검표 성공 일시 |
| `cancelled_at` | 취소 일시 |
| `replaced_by_ticket_id` | 재발급된 새 티켓 FK |
| `created_at` | 생성 일시 |
| `updated_at` | 최종 수정 일시 |

### 4.23 `ticket_validation_logs` — 모의 티켓 검표 이력

| 컬럼이름 | 설명 |
| --- | --- |
| `ticket_validation_log_id` | 티켓 검표 로그 PK |
| `issued_ticket_id` | 일치한 발급 티켓 FK, 일치하지 않으면 NULL |
| `validator_user_id` | 검표한 관리자 FK |
| `presented_token_fingerprint` | 제출된 토큰의 비가역 지문 |
| `validation_result` | `SUCCESS`, `NOT_FOUND`, `ALREADY_USED`, `CANCELLED`, `EXPIRED` 중 하나 |
| `validation_channel` | `ADMIN_WEB`, `MOCK_SCANNER`, `API` 중 하나 |
| `device_id` | 모의 스캐너 또는 클라이언트 장치 ID |
| `failure_reason` | 검표 실패 사유 |
| `metadata` | 추가 검표 문맥 JSON |
| `validated_at` | 검표 시도 일시 |

## 5. 구현 시 주의사항

- `trips` 생성 시 시작일부터 종료일까지의 `trip_days`를 생성하고, 날짜 범위 유효성은 서비스 계층에서도 검사한다. 서로 다른 행을 참조하는 규칙은 CHECK만으로 보장할 수 없다.
- 본 프로젝트의 예약·결제·발권은 포트폴리오용 모의 시스템이며 실제 티켓 공급사나 PG사와 연동하지 않는다.
- 모의 결제가 확정되면 구매 수량만큼 개별 전자 티켓을 발급하고, 모바일 QR과 인쇄 티켓은 같은 검증 체계를 사용한다.
- QR에는 DB PK나 토큰 원문을 그대로 신뢰하지 않는다. 무작위 토큰은 최초 발급 시에만 전달하고 DB에는 해시를 저장한다.
- 검표 시 `UPDATE issued_tickets SET status = 'USED', used_at = CURRENT_TIMESTAMP(6) WHERE issued_ticket_id = ? AND status = 'ISSUED'`와 같은 조건부 갱신으로 중복 사용을 막는다.
- 결제 대기 예약 생성, 재고 확보, 만료 재고 복구는 반드시 트랜잭션과 멱등 키 전략으로 구현한다. Redis 락을 쓰더라도 MySQL의 조건부 재고 갱신을 최종 방어선으로 둔다.
- 장소 수집 작업은 `(external_provider, external_place_id)` 기준 UPSERT로 처리하고, 작업 집계 수치와 오류 행 갱신을 원본 처리 트랜잭션 경계에 맞춘다.
- `admin_audit_logs`는 애플리케이션에서 UPDATE·DELETE를 허용하지 않고, 민감정보와 비밀번호·결제 인증값은 JSON 스냅샷에 기록하지 않는다.
- `payments`는 재시도를 고려해 예약 1건에 여러 행을 허용한다. PG 웹훅 처리 시 `(provider, provider_payment_key)` 유일 키로 중복 반영을 방지한다.
- 날씨·교통처럼 빠르게 만료되는 외부 API 결과는 Redis 캐시가 적합하다. 장기 분석이나 감사가 필요해지면 별도 스냅샷 테이블을 추가한다.
- `place_travel_styles.source = AI`인 태그는 모델·프롬프트 변경 후 재산출할 수 있도록 배치 버전 관리가 필요하다.
- `recommendation_events`의 `IMPRESSION`은 노출 시점에 반드시 기록해야 클릭률과 일정 추가 전환율을 올바르게 계산할 수 있다. `metadata`에는 최소 추천 순위와 점수를 저장한다.
- RAG 문서와 벡터는 Vector DB에서 관리하고, 향후 출처 추적이 필요하면 별도의 `rag_documents` 메타데이터 테이블을 추가한다.

## 6. 관리자 기능 검토사항

> 이 절은 관리자 기능 구현을 위한 현재 범위와 향후 검토사항을 구분한다. 장소 동기화 작업·오류 및 관리자 감사 로그는 현재 DDL에 반영했으며, 나머지 후보는 기능 범위가 확정된 뒤 별도 변경 이력으로 추가한다.

### 6.1 현재 스키마로 구현 가능한 기능

| 관리자 기능 | 관련 테이블·컬럼 | 설명 |
| --- | --- | --- |
| 관리자 인증·권한 확인 | `users.role` | `ADMIN` 권한을 가진 회원만 관리자 화면과 API에 접근하도록 제한한다. |
| 장소 목록·검색·상세 조회 | `places` | 지역, 도시, 카테고리, 장소명과 노출 상태를 기준으로 조회한다. |
| 장소 등록·수정 | `places` | 장소 기본 정보, 주소, 좌표, 외부 제공자 식별자와 설명을 관리한다. |
| 장소 노출 관리 | `places.is_active` | 잘못된 장소, 폐업 장소 또는 검수 중인 장소의 사용자 노출을 중지한다. |
| 장소 이미지 관리 | `place_images` | 이미지 URL, 대표 이미지 여부와 표시 순서를 관리한다. |
| 여행 스타일 코드 관리 | `travel_styles` | 스타일 이름, 설명, 활성 여부와 표시 순서를 관리한다. 코드 변경은 애플리케이션 영향도를 검토한 뒤 수행한다. |
| 장소별 스타일 관리 | `place_travel_styles` | 장소에 연결된 여행 스타일, 적합도와 생성 출처를 검토·수정한다. |
| AI 스타일 태그 검수 | `place_travel_styles.source` | `AI`로 생성된 태그를 조회하고 운영자가 적합도를 보정한다. 현재 구조에는 승인 상태가 없으므로 직접 수정 방식으로 운영한다. |
| 추천 행동 조회 | `recommendation_events` | 장소별 노출, 클릭, 즐겨찾기 및 일정 추가 행동을 조회해 추천 품질을 점검한다. |
| 티켓 상품·재고 조회 | `ticket_products`, `ticket_inventory` | 상품 상태와 판매 기간, 총수량 및 예약수량을 확인한다. 재고 수동 변경은 별도 정책과 감사 이력 없이 제공하지 않는다. |
| 모의 티켓 발급·검표 | `issued_tickets`, `ticket_validation_logs` | 모바일·인쇄 티켓을 조회·재발급하고 관리자 웹 검표 결과와 중복 사용 시도를 확인한다. |
| 장소 수집 작업 관리 | `place_sync_jobs`, `place_sync_errors` | 외부 데이터 동기화를 실행하고 진행 상태, 처리 건수와 레코드별 실패·재처리 상태를 확인한다. |
| 관리자 변경 이력 조회 | `admin_audit_logs` | 장소·스타일 등 운영 데이터의 변경 전후 값과 요청 정보를 조회한다. |

### 6.2 MVP 관리자 화면 권장 범위

1. 관리자 로그인 및 `ADMIN` 권한 검사
2. 장소 목록, 조건 검색 및 상세 조회
3. 장소 등록·수정과 `is_active` 기반 노출 관리
4. 장소 이미지 URL, 대표 이미지 및 표시 순서 관리
5. 장소별 여행 스타일과 적합도 관리
6. AI 생성 스타일 태그 조회 및 운영자 보정

### 6.3 향후 스키마 추가 검토 대상

| 추가 후보 테이블·컬럼 | 필요 목적 | 도입 시점 |
| --- | --- | --- |

| `place_travel_styles.review_status` 및 검수자 컬럼 | AI 생성 태그의 대기·승인·반려 상태와 검수자 추적 | AI 태그 승인 워크플로 도입 시 |
| 장소 병합 이력 구조 | 중복 장소의 대표 장소 지정과 기존 참조 이전 기록 | 중복 병합 기능 구현 시 |

### 6.4 운영 및 보안 원칙

- 관리자 API는 화면 숨김만으로 보호하지 않고 Spring Security에서 `ADMIN` 권한을 강제한다.
- 외부 데이터 동기화는 `(external_provider, external_place_id)` 유일 키를 기준으로 멱등하게 처리한다.
- 장소 및 스타일의 대량 변경은 미리보기와 확인 단계를 제공하고, 트랜잭션 단위로 처리한다.
- 운영자 변경 이력 구조가 추가되기 전에는 티켓 재고 수동 조정처럼 금전적 영향이 있는 기능을 제공하지 않는다.
- 관리자 기능 도입으로 스키마를 변경할 때 DDL과 이 문서의 테이블·컬럼 사전을 같은 변경에서 함께 갱신한다.

## 7. 포트폴리오용 모의 티켓 범위

> 본 프로젝트의 티켓 기능은 예약 동시성, 대기열, 모의 결제, 전자 발권 및 중복 검표 방지를 학습·시연하기 위한 기능이다. 실제 티켓 공급사의 판매·발권 권한을 위임받거나 실제 PG 결제를 처리하지 않는다.

| 포함 범위 | 제외 범위 |
| --- | --- |
| 관리자가 등록한 가상 관광·체험 티켓 상품 | 실제 관광시설 및 티켓 공급사 계약 |
| 한정 재고 예약과 초과 판매 방지 | 공급사 재고·발권 API 연동 |
| 성공·실패·지연을 재현하는 Mock 결제 | 실제 PG 결제와 정산 |
| 구매 수량별 고유 전자 티켓 발급 | 실제 판매 수수료와 세금계산서 처리 |
| 모바일 QR 표시 및 인쇄 가능한 티켓 | 법적 효력이 있는 실제 입장권 발급 |
| 관리자 웹 또는 모의 스캐너 검표 | 실제 시설의 오프라인 검표 단말기 연동 |
| 중복 검표 차단, 취소, 만료 및 재발급 상태 | 공급사별 취소·환불·정산 정책 |

### 모의 처리 흐름

```text
관리자 모의 상품·재고 등록
      ↓
사용자 예약 및 재고 확보
      ↓
Mock 결제 성공·실패 처리
      ↓
결제 성공 시 수량별 전자 티켓 발급
      ↓
모바일 QR 표시 또는 종이 출력
      ↓
관리자 화면·Mock Scanner 검표
      ↓
ISSUED → USED 원자적 상태 변경
      ↓
성공·실패를 포함한 모든 검표 시도 기록
```
