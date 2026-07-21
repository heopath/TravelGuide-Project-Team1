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
| `places` → `place_images`, `users` + `places` → `favorites` (장소·즐겨찾기) | 외부 관광 데이터와 서비스 자체 장소를 통합하고, 장소 이미지 및 회원별 즐겨찾기를 관리한다. |
| `users` → `trips` → `trip_days` → `itinerary_items`, `trips` + `travel_styles` → `trip_travel_styles` (여행 일정) | 여행 입력 조건, 날짜별 구획, 순서가 있는 활동을 계층화한다. 일정 항목은 장소를 선택적으로 참조해 장소가 삭제되어도 제목 스냅샷을 보존한다. |
| `users` → `ai_generation_requests` → `trips` (AI) | 일정 생성, 동선 최적화, 챗봇 요청의 입력·결과·모델·토큰·상태를 감사 가능하게 기록한다. 실패하거나 아직 여행이 만들어지지 않은 요청은 `trip_id`가 없을 수 있다. |
| `places` → `ticket_products` → `ticket_inventory`, `users` → `reservations` → `reservation_items` ← `ticket_products`, `reservations` → `payments` (예약·결제) | 장소별 티켓, 단일 집계 재고, 주문 스냅샷과 결제 시도를 관리한다. 재고의 `version`으로 낙관적 락을 수행하며 실제 차감은 트랜잭션에서 처리한다. |

## 3. 핵심 관계 및 무결성

- 회원 이메일·닉네임, 여행 스타일 코드, 외부 제공자별 장소 ID, 즐겨찾기 `(user_id, place_id)`는 중복될 수 없다.
- 여행 종료일은 시작일보다 빠를 수 없고, 여행별 일차 번호 및 날짜는 각각 유일하다.
- 일차 안의 `sort_order`는 유일하며 순서 변경 시 관련 행을 한 트랜잭션에서 갱신한다.
- 티켓 재고는 `reserved_quantity <= total_quantity`를 항상 만족해야 한다. `UPDATE ... WHERE version = ? AND reserved_quantity + ? <= total_quantity` 형태의 조건부 갱신을 권장한다.
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

## 5. 구현 시 주의사항

- `trips` 생성 시 시작일부터 종료일까지의 `trip_days`를 생성하고, 날짜 범위 유효성은 서비스 계층에서도 검사한다. 서로 다른 행을 참조하는 규칙은 CHECK만으로 보장할 수 없다.
- 결제 대기 예약 생성, 재고 확보, 만료 재고 복구는 반드시 트랜잭션과 멱등 키 전략으로 구현한다. Redis 락을 쓰더라도 MySQL의 조건부 재고 갱신을 최종 방어선으로 둔다.
- `payments`는 재시도를 고려해 예약 1건에 여러 행을 허용한다. PG 웹훅 처리 시 `(provider, provider_payment_key)` 유일 키로 중복 반영을 방지한다.
- 날씨·교통처럼 빠르게 만료되는 외부 API 결과는 Redis 캐시가 적합하다. 장기 분석이나 감사가 필요해지면 별도 스냅샷 테이블을 추가한다.
- RAG 문서와 벡터는 Vector DB에서 관리하고, 향후 출처 추적이 필요하면 별도의 `rag_documents` 메타데이터 테이블을 추가한다.
