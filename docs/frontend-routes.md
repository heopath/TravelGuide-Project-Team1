# 프론트 화면 연결 안내

## 실행

기본 프로필은 `ui`이며 PostgreSQL, Redis, Gemini API 키가 없어도 화면을 확인할 수 있다.

```shell
./gradlew bootRun
```

브라우저에서 `http://localhost:8080`으로 접속하면 `/home`으로 이동한다.

## 화면 URL

| URL | Thymeleaf 템플릿 |
| --- | --- |
| `/home` | `home/home.html` |
| `/auth/login` | `auth/login.html` |
| `/auth/signup` | `auth/signup.html` |
| `/trips/new/basic` | `trips/basic.html` |
| `/trips/new/style` | `trips/style.html` |
| `/trips/recommendations` | `trips/recommendations.html` |
| `/trips/{tripSlug}/schedule` | `trips/schedule.html` |
| `/trips/{tripSlug}/map` | `trips/map.html` |
| `/trips/{tripSlug}/optimize` | `trips/optimize.html` |
| `/trips/{tripId}/record` | `trips/record.html` |
| `/guide` | `guide/guide.html` |
| `/guide/themes` | `guide/themes.html` |
| `/guide/places/{placeSlug}` | `guide/place-detail.html` |
| `/ai-guide` | `guide/ai-guide.html` |
| `/booking` | `booking/booking.html` |
| `/booking/tickets/{ticketSlug}` | `booking/ticket.html` |
| `/booking/hotels` | `booking/hotels.html` |
| `/booking/flights` | `booking/flights.html` |
| `/booking/queue` | `booking/queue.html` |
| `/mypage` | `mypage/mypage.html` |
| `/admin` | `admin/admin.html` |

## 화면별 PostgreSQL 영속화 검토

| 화면 | 사용하는 비즈니스 테이블 또는 저장 전략 |
| --- | --- |
| 홈·여행 기본 정보·스타일 | `trips`, `trip_travel_styles`, `travel_styles`, `user_preferences` |
| 여행지 추천 | `recommendation_sessions`, `recommendation_results`, `recommendation_events`, `places` |
| 일정·지도 | `trips`, `trip_days`, `itinerary_items`, `places` |
| 일정 공유 모달 | `trip_share_links`에 토큰 해시·만료·폐기 상태 저장 |
| AI 일정 최적화 | `ai_generation_requests`, `trips`, `itinerary_items` |
| 여행 기록 | `travel_records`, `travel_record_images` 및 소셜 하위 테이블 |
| 여행 가이드·장소 상세 | `places`, `place_images`, `place_travel_styles`, `favorites` |
| 테마 여행·관리자 테마 등록 | `travel_themes`, `travel_theme_styles`, `travel_theme_places`, `places` |
| AI 여행 가이드 | `ai_chat_sessions`, `ai_chat_messages`, `ai_generation_requests`; 임베딩은 Spring AI `vector_store` |
| 예약 허브·티켓 상세 | `ticket_products`, `ticket_product_options`, `ticket_time_slots`, `ticket_inventory` |
| 티켓 예약·모의 결제·발권 | `reservations`, `reservation_items`, `payments`, `issued_tickets`, `ticket_validation_logs` |
| 항공·숙소 검색 | 외부 검색 링크만 제공하므로 전용 예약 테이블 없음 |
| 예약 대기열 | Redis 순번·입장 토큰·TTL 사용, PostgreSQL 테이블 없음 |
| 마이페이지 | 회원·여행·즐겨찾기·기록·예약 도메인의 조회 API 조합 |
| 관리자 | `travel_themes`, 장소 동기화·신고·티켓 운영 테이블, `admin_audit_logs` |

날씨·교통은 실시간 외부 API 응답이며 영속 이력이 요구되기 전까지 PostgreSQL 테이블을 만들지 않는다.

## 리소스 위치

- HTML: `src/main/resources/templates`
- CSS: `src/main/resources/static/css`
- JavaScript: `src/main/resources/static/js`
- 이미지: `src/main/resources/static/images`

`PageController`는 화면 반환만 담당한다. 이후 REST API Controller와 DTO는 기능별 패키지에 별도로 작성한다.
