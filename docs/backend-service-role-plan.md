# All My Trips 백엔드 도메인·Service 역할 분담 초안

> DB 설계와 화면 흐름을 기준으로 초기 Service 계층의 책임과 팀원별 담당 영역을 정의한다. 메서드 인자·반환 DTO는 API 설계 과정에서 변경할 수 있지만, 도메인 소유권과 의존 방향은 팀 합의 없이 바꾸지 않는다.

## 1. 기준과 적용 범위

- 서비스·팀원: 저장소 루트 `README.md`
- DB 도메인·테이블: `docs/database/all_my_trips_database.md`
- 화면 경로: `docs/frontend-routes.md`
- 1차: 회원·장소·여행·AI 추천·여행 기록
- 2차: 관리자 운영, 관광 티켓·예약·모의 결제·발권
- 3차: RAG 개인화, 행동 기반 선호 추론, 동선 최적화

## 2. Service 통합 검토 기준

Service 개수는 테이블 수가 아니라 Aggregate Root, 트랜잭션 경계, 권한 정책, 외부 시스템 연동 여부를 기준으로 정한다.

| 검토 대상 | 결정 | 이유 |
| --- | --- | --- |
| 회원과 인증 | `AuthService`, `MemberService` 분리 | 인증은 Spring Security·비밀번호·세션 정책, 회원은 프로필·선호 관리로 변경 이유가 다름 |
| 장소·이미지·장소 스타일 | `PlaceService`로 통합 | 장소가 이미지와 스타일 적합도의 Aggregate Root이며 함께 조회·수정됨 |
| 즐겨찾기 | `FavoriteService` 분리 | 회원과 장소 사이의 사용자별 관계이며 장소 관리 권한과 다름 |
| 장소 외부 동기화 | `PlaceSyncService` 분리 | 배치 실행·실패 재처리·외부 API라는 별도 트랜잭션 경계를 가짐 |
| 여행·스타일·일차·일정 | `TripService`로 통합 | `Trip`이 하위 일차·일정·스타일의 생명주기와 소유권을 통제하는 Aggregate Root |
| AI 일정 생성 | `AiTravelService` 분리 | 외부 모델 호출, 장애·재시도·토큰 이력이라는 별도 책임을 가짐 |
| 추천과 추천 행동 | `RecommendationService`로 통합 | 추천 생성과 행동 수집이 같은 추천 세션·결과 문맥을 사용함 |
| 여행 기록·이미지·댓글·반응 | `TravelRecordService`로 통합 | 여행 기록이 이미지·댓글·좋아요·공유의 Aggregate Root |
| 여행 기록 신고 | `TravelRecordReportService` 분리 | 사용자 신고와 관리자 심사 상태 전환이 별도 권한·감사 정책을 가짐 |
| 티켓 상품·재고·발권·검표 | `TicketService`로 통합 | 티켓 상품과 발급 티켓의 사용 가능 여부를 한 도메인 정책으로 관리 |
| 예약 | `ReservationService` 유지 | 재고 확보·주문 스냅샷·예약 상태 전환의 트랜잭션 중심 |
| 결제 | `PaymentService` 분리 | Mock PG라도 결제 승인·취소·멱등성은 예약과 다른 외부 연동 경계 |
| 대기열 | `BookingQueueService` 분리 | Redis TTL·순번·입장 토큰은 PostgreSQL 예약 트랜잭션과 다른 저장소를 사용 |
| 관리자 공통 | `AdminService`로 통합 | 권한 검사·감사 로그·대시보드는 공통 운영 진입점으로 묶음 |

통합 후 공개 Service는 15개다. 내부 구현이 복잡해지면 공개 인터페이스를 늘리기보다 먼저 도메인 내부 정책 클래스나 컴포넌트로 분리한다.

## 3. 패키지와 충돌 방지 원칙

```text
org.example.all_my_trip_project
├── common/          # 공통 예외·응답·시간·보안 사용자 조회
├── member/          # AuthService, MemberService
├── place/           # PlaceService, FavoriteService, PlaceSyncService
├── trip/            # TripService
├── recommendation/  # AiTravelService, RecommendationService
├── record/          # TravelRecordService, TravelRecordReportService
├── booking/         # TicketService, ReservationService, PaymentService, BookingQueueService
└── admin/           # AdminService
```

각 도메인 내부 권장 구조:

```text
trip/
├── controller/
├── service/
│   ├── TripService.java
│   └── TripServiceImpl.java
├── domain/
├── repository/
├── dto/
└── exception/
```

규칙:

1. 담당자는 자신의 주 담당 패키지 안에서 작업한다.
2. 다른 도메인의 Repository를 직접 호출하지 않고 공개 Service 또는 조회 Port를 사용한다.
3. Entity를 Controller 응답으로 직접 반환하지 않고 Result DTO로 변환한다.
4. Service에 `HttpServletRequest`, `Model`, `ResponseEntity` 같은 웹 계층 타입을 전달하지 않는다.
5. 외부 AI·결제·Redis는 Port/Client로 감싸 도메인 Service가 SDK에 직접 종속되지 않게 한다.
6. 공통 클래스와 다른 도메인의 공개 인터페이스 변경은 담당자 합의 후 반영한다.
7. 도메인 간 순환 의존을 만들지 않는다.

## 4. 도메인별 기능과 Service

### 4.1 회원·인증·선호

관련 테이블: `users`, `travel_styles`, `user_preferences`

| Service | 책임 |
| --- | --- |
| `AuthService` | 회원가입, 이메일·닉네임 중복 검사, 비밀번호 인코딩, 로그인 검증, 로그아웃 후처리 |
| `MemberService` | 현재 회원 조회, 프로필 수정, 탈퇴, 계정 상태, 활성 여행 스타일와 사용자 선호 조회·변경·추론 점수 반영 |

`CurrentMemberService`는 별도 비즈니스 Service로 두지 않고 `common.security.CurrentUserProvider` 컴포넌트로 제공한다.

### 4.2 장소·즐겨찾기·동기화

관련 테이블: `places`, `place_images`, `place_travel_styles`, `favorites`, `place_sync_jobs`, `place_sync_errors`

| Service | 책임 |
| --- | --- |
| `PlaceService` | 장소 검색·상세, 이미지·대표 이미지, 장소 스타일 적합도, 관리자 장소 등록·수정·노출 관리, 캐시 무효화 |
| `FavoriteService` | 회원별 즐겨찾기 추가·해제·목록 조회 |
| `PlaceSyncService` | 외부 장소 수집, 진행 상태, 처리 건수, 실패 기록·재처리 |

Redis 캐시는 `PlaceCacheStore` 내부 컴포넌트로 두고 Controller가 직접 호출하지 않는다.

### 4.3 여행 계획·일정

관련 테이블: `trips`, `trip_travel_styles`, `trip_days`, `itinerary_items`, `trip_share_links`

| Service | 책임 |
| --- | --- |
| `TripService` | 여행 CRUD, 소유권, 상태 전환, 여행 스타일, 기간별 일차 생성, 일정 항목 CRUD·순서 변경·시간·비용 검증 |

`Trip`이 Aggregate Root이므로 일차와 일정 항목의 변경은 모두 `TripService`를 통해 처리한다. 구현체 내부에는 `TripOwnershipPolicy`, `TripPeriodPolicy`, `ItineraryOrderPolicy` 같은 정책 객체를 둘 수 있다.

### 4.4 AI·추천

관련 테이블: `ai_chat_sessions`, `ai_chat_messages`, `ai_generation_requests`, `recommendation_sessions`, `recommendation_results`, `recommendation_events`; 참조: `user_preferences`, `place_travel_styles`, `places`, `trips`

| Service | 책임 |
| --- | --- |
| `AiTravelService` | AI 일정 생성·재생성·동선 최적화·챗봇 요청, 요청 상태·오류·토큰 이력 저장 |
| `RecommendationService` | 선호·여행 조건 기반 장소 추천, 노출·클릭·즐겨찾기·일정 추가 이벤트 기록 |

`TravelAiClient`, `PlaceRetrievalPort`는 외부 연동 Port이며 Service 역할 분담 수에 포함하지 않는다. 추천 순위·점수·이유는 PostgreSQL의 `recommendation_sessions`, `recommendation_results`에 정규화하고, 이벤트의 가변 부가 문맥만 `JSONB`로 유지한다.

### 4.5 여행 기록·소셜

관련 테이블: `travel_records`, `travel_record_images`, `travel_record_comments`, `travel_record_likes`, `travel_record_shares`, `travel_record_reports`

| Service | 책임 |
| --- | --- |
| `TravelRecordService` | 완료 여행 기록 CRUD, 공개 범위, 이미지·대표 이미지, 댓글·답글, 좋아요, 공유 이벤트, 소프트 삭제 |
| `TravelRecordReportService` | 신고 접수, 처리 중 중복 신고 차단, 관리자 검토·해결·반려와 처리 메모 |

여행 기록 작성 시 `TripService`의 완료 여행·소유자 조회 계약을 사용한다. 기록 하위 기능은 `TravelRecordService` 구현체 내부 정책으로 나눈다.

### 4.6 관광 티켓·예약·결제 — 2차

관련 테이블: `ticket_products`, `ticket_product_options`, `ticket_time_slots`, `ticket_inventory`, `reservations`, `reservation_items`, `payments`, `issued_tickets`, `ticket_validation_logs`

| Service | 책임 |
| --- | --- |
| `TicketService` | 상품 조회·관리, 재고 조회·조건부 확보·복구, 전자 티켓 발급·재발급·검표·중복 사용 차단 |
| `ReservationService` | 예약 생성·조회·확정·취소·만료, 구매 당시 상품명·가격 스냅샷 |
| `PaymentService` | Mock 결제 요청·승인·실패·취소·환불, 중복 요청 방지 |
| `BookingQueueService` | Redis 대기열 등록·순번·입장 허용·TTL 만료 |

재고 확보는 `ReservationService`가 `TicketService`의 공개 계약을 호출한다. 결제 성공 후 예약 확정과 발권 연결은 애플리케이션 이벤트 또는 명시적 오케스트레이션으로 처리한다.

### 4.7 관리자 공통 운영

관련 테이블: `travel_themes`, `travel_theme_styles`, `travel_theme_places`, `admin_audit_logs`; 참조: 장소 동기화·신고·티켓 운영 테이블

| Service | 책임 |
| --- | --- |
| `AdminService` | ADMIN 권한 확인, 변경 전후 감사 로그, 장소 수집·신고·티켓 운영 상태 대시보드 조회 |

각 도메인은 관리자 작업 성공 후 `AdminService.recordAudit()`을 호출한다. 비밀번호·인증 토큰·결제 인증값은 감사 JSON에 기록하지 않는다.

## 5. 팀원별 역할 분배

### 1차 주 담당

| 팀원 | 주 담당 | 공개 Service | 주요 협업 계약 |
| --- | --- | --- | --- |
| 허민재 | 회원·인증·선호·공통 보안 | `AuthService`, `MemberService` | 인증 사용자와 권한 조회 계약을 전체 도메인에 먼저 제공 |
| 정인길 | 장소·즐겨찾기·장소 운영 | `PlaceService`, `FavoriteService`, `PlaceSyncService` | AI·여행·티켓에 장소 조회 계약 제공 |
| 홍유원 | 여행 계획·일차·일정 | `TripService` | AI와 여행 기록에 여행·소유권·완료 상태 계약 제공 |
| 남현호 | 여행 기록·소셜·신고 | `TravelRecordService`, `TravelRecordReportService` | 회원 인증과 완료 여행 조회 계약 사용 |
| 한성주 | AI·추천·RAG 연결 | `AiTravelService`, `RecommendationService` | 회원 선호·장소 후보·여행 저장 계약 조합 |

### 2차 담당

| 영역 | 주 담당 | 보조 담당 |
| --- | --- | --- |
| 티켓 상품·재고·발권·검표 | 정인길 | 홍유원 |
| 예약·모의 결제 | 홍유원 | 남현호 |
| Redis 대기열·부하 테스트 | 한성주 | 정인길 |
| 관리자 공통·감사 | 허민재 | 남현호 |

2차는 1차 주 담당 기능이 안정화된 후 시작한다.

## 6. 구현 순서

| 순서 | 구현 | 완료 기준 |
| ---: | --- | --- |
| 0 | 공통 예외·응답·시간·테스트 규칙 | 모든 도메인이 사용할 최소 공통 계약 합의 |
| 1 | `AuthService` | 회원가입, 비밀번호 인코딩, 로그인 성공·실패, 로그아웃, 보안 테스트 |
| 2 | `MemberService` | 현재 회원, 프로필, 계정 상태, 스타일·선호 저장 |
| 3 | `PlaceService` | 지역·카테고리 검색, 상세·이미지·스타일 조회 |
| 4 | `TripService` 기본 | 소유자 기준 여행 CRUD, 기간 검증, 일차 자동 생성 |
| 5 | `TripService` 일정 | 일정 CRUD, 순서·시간·비용 검증 |
| 6 | `FavoriteService`, 추천 이벤트 기반 | 즐겨찾기 중복 방지, 추천 행동 기록 |
| 7 | `AiTravelService`, `RecommendationService` | AI 성공·실패 이력, 장소 추천 결과 |
| 8 | `TravelRecordService` | 완료 여행 소유자만 기록 1건 작성, 이미지·댓글·반응 |
| 9 | `TravelRecordReportService`, `PlaceSyncService`, `AdminService` | 신고 처리, 장소 수집, 감사 로그 |
| 10 | 티켓·예약·결제 Service | 재고 초과·중복 발권·중복 검표 차단 |
| 11 | RAG·동선 최적화 | Vector 검색, 재정렬, 추천 이유 |

인증 계약이 확정된 뒤 장소·여행·AI Client Mock은 병렬로 시작할 수 있다.

## 7. Service 인터페이스와 DTO 계약

### DTO 사용 원칙

- `Command`: Service에 변경을 요청하는 입력 객체다. Controller 입력을 검증한 뒤 Service로 전달한다.
- `Condition`: 검색·필터 조건이다. 데이터 변경 의미를 갖지 않는다.
- `Result`: Service가 외부에 반환하는 읽기 전용 결과다. Entity를 노출하지 않는다.
- ID는 초기 템플릿에서 `long`을 사용하며 인증된 사용자 ID는 Controller가 임의로 받지 않고 `CurrentUserProvider`에서 얻는다.
- 문자열 상태는 가능한 한 Java `enum`으로 정의해 DB CHECK 값과 일치시킨다.
- 페이지 결과는 공통 `PageResult<T>`를 사용한다.

### 7.1 `AuthService`

```java
public interface AuthService {
    SignupResult signup(SignupCommand command);
    LoginResult login(LoginCommand command);
    void logout(long userId);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `SignupCommand` | `email`, `rawPassword`, `nickname` | 이메일 형식, 비밀번호 정책, 닉네임 길이를 검증한다. 원문 비밀번호는 저장·로그 출력하지 않는다. |
| `SignupResult` | `userId`, `email`, `nickname`, `role` | 가입 완료 후 공개 가능한 회원 정보만 반환한다. `passwordHash`는 포함하지 않는다. |
| `LoginCommand` | `email`, `rawPassword` | 인증용 입력이다. 실패 이유로 이메일 존재 여부를 과도하게 노출하지 않는다. |
| `LoginResult` | `userId`, `nickname`, `role`, `status` | 인증 성공 결과다. 세션·토큰 방식이 정해지면 인증 식별자를 추가할 수 있다. |

```java
public record SignupCommand(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 72) String rawPassword,
        @NotBlank @Size(max = 50) String nickname
) {}

public record SignupResult(long userId, String email, String nickname, UserRole role) {}
```

### 7.2 `MemberService`

```java
public interface MemberService {
    MemberProfileResult getProfile(long userId);
    MemberProfileResult updateProfile(long userId, UpdateMemberProfileCommand command);
    UserPreferenceResult replacePreferences(long userId, UpdatePreferencesCommand command);
    void applyInferredPreference(ApplyInferredPreferenceCommand command);
    void withdraw(long userId, WithdrawMemberCommand command);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `UpdateMemberProfileCommand` | `nickname` | 닉네임 중복과 길이를 검증한다. |
| `UpdatePreferencesCommand` | `preferences` | 스타일 ID와 0~100 점수 목록이다. 동일 스타일 중복을 금지한다. |
| `ApplyInferredPreferenceCommand` | `userId`, `travelStyleId`, `scoreDelta`, `reason` | 추천 행동으로 선호도를 조정하는 내부 명령이다. 최종 점수는 0~100으로 제한한다. |
| `MemberProfileResult` | 회원 ID·이메일·닉네임·역할·상태 | 마이페이지 표시용이며 비밀번호와 삭제 내부 정보는 제외한다. |
| `UserPreferenceResult` | 스타일 ID·코드·이름·점수·출처 목록 | 명시 선호와 추론 선호를 화면과 추천 도메인에 제공한다. |

### 7.3 `PlaceService`, `FavoriteService`, `PlaceSyncService`

```java
public interface PlaceService {
    PageResult<PlaceSummaryResult> search(PlaceSearchCondition condition, PageRequest page);
    PlaceDetailResult getPlace(long placeId);
    PlaceDetailResult create(long adminUserId, UpsertPlaceCommand command);
    PlaceDetailResult update(long adminUserId, long placeId, UpsertPlaceCommand command);
    void changeActive(long adminUserId, long placeId, boolean active);
}

public interface FavoriteService {
    FavoriteResult add(long userId, long placeId, AddFavoriteCommand command);
    void remove(long userId, long placeId);
    PageResult<FavoriteResult> getFavorites(long userId, PageRequest page);
}

public interface PlaceSyncService {
    PlaceSyncJobResult start(long adminUserId, StartPlaceSyncCommand command);
    PlaceSyncJobResult getJob(long jobId);
    void retryError(long adminUserId, long errorId);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `PlaceSearchCondition` | `keyword`, `region`, `city`, `category`, `styleId`, `activeOnly` | 조회 전용 필터다. 사용자 화면은 기본적으로 활성 장소만 조회한다. |
| `UpsertPlaceCommand` | 이름·카테고리·주소·좌표·URL·이미지·스타일 점수 | 관리자 입력이다. 위경도 범위, URL 형식, 스타일 점수 0~100을 검증한다. |
| `PlaceDetailResult` | 장소 기본 정보·이미지·스타일 목록·외부 링크 | 장소 Aggregate를 한 번에 보여주는 결과다. |
| `AddFavoriteCommand` | `memo` | 사용자 메모는 선택이며 최대 길이를 제한한다. 사용자·장소 중복은 DB UNIQUE로 최종 방어한다. |
| `StartPlaceSyncCommand` | `provider`, `jobType` | 수집 제공자와 `FULL`, `INCREMENTAL`, `MANUAL` 유형을 지정한다. |
| `PlaceSyncJobResult` | 상태·요청/처리/성공/실패 건수·시간 | 관리자 작업 진행 상황을 반환한다. |

### 7.4 `TripService`

```java
public interface TripService {
    TripResult create(long userId, CreateTripCommand command);
    TripDetailResult get(long userId, long tripId);
    TripResult update(long userId, long tripId, UpdateTripCommand command);
    TripResult changeStatus(long userId, long tripId, ChangeTripStatusCommand command);
    ItineraryItemResult addItem(long userId, long tripId, long tripDayId,
                                AddItineraryItemCommand command);
    ItineraryItemResult updateItem(long userId, long tripId, long itemId,
                                   UpdateItineraryItemCommand command);
    void removeItem(long userId, long tripId, long itemId);
    List<ItineraryItemResult> reorderItems(long userId, long tripId, long tripDayId,
                                           ReorderItineraryCommand command);
    void delete(long userId, long tripId);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `CreateTripCommand` | 제목·목적지·시작/종료일·동행 유형/인원·예산·스타일 ID | 종료일이 시작일보다 빠를 수 없고 인원·예산은 음수가 될 수 없다. 생성 시 기간만큼 일차를 함께 만든다. |
| `UpdateTripCommand` | 수정 가능한 여행 조건 | `COMPLETED`, `CANCELLED` 이후 수정 허용 범위를 정책으로 제한한다. |
| `ChangeTripStatusCommand` | `targetStatus` | 허용된 상태 전이만 받는다. 기록이 존재하는 완료 여행의 상태 되돌리기를 제한한다. |
| `AddItineraryItemCommand` | 장소 ID·유형·제목·시작/종료 시각·메모·비용 | 장소는 선택값이며 종료 시각은 시작 시각보다 빠를 수 없다. 순서는 Service가 계산한다. |
| `ReorderItineraryCommand` | 정렬된 `itemIds` | 해당 일차의 전체 항목을 중복·누락 없이 전달해야 한다. 한 트랜잭션에서 순서를 변경한다. |
| `TripDetailResult` | 여행·스타일·일차·정렬된 일정 목록 | 여행 Aggregate 전체를 조회하는 결과다. |

```java
public record CreateTripCommand(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 150) String destinationName,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull CompanionType companionType,
        @Min(1) int companionCount,
        @PositiveOrZero BigDecimal budgetAmount,
        @NotEmpty List<Long> travelStyleIds
) {}

public record ReorderItineraryCommand(@NotEmpty List<Long> itemIds) {}
```

### 7.5 `AiTravelService`, `RecommendationService`

```java
public interface AiTravelService {
    AiItineraryResult generate(long userId, GenerateItineraryCommand command);
    AiItineraryResult optimize(long userId, long tripId, OptimizeRouteCommand command);
    AiChatResult chat(long userId, AiChatCommand command);
}

public interface RecommendationService {
    RecommendationResult recommend(long userId, RecommendPlacesCommand command);
    void recordEvent(long userId, RecordRecommendationEventCommand command);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `GenerateItineraryCommand` | 여행 조건 또는 `tripId`, 추가 요구사항 | AI 입력을 구성하지만 민감정보를 포함하지 않는다. 요청 전에 사용자 소유권을 확인한다. |
| `AiItineraryResult` | 요청 ID·상태·생성된 일차/일정 초안·오류 | AI 원본 JSON을 직접 노출하지 않고 서비스에서 해석한 결과를 반환한다. |
| `RecommendPlacesCommand` | `tripId`, 지역·스타일·카테고리·후보 수 | 사용자 선호와 여행 조건을 결합하기 위한 입력이다. 후보 수에 상한을 둔다. |
| `RecommendationResult` | 추천 세션 ID·장소 결과 목록·추천 이유 | 각 결과는 장소 ID·순위·점수·이유를 갖는다. 정규화 테이블 도입 시 구조를 유지할 수 있다. |
| `RecordRecommendationEventCommand` | 세션/결과 식별자·장소 ID·이벤트 유형·문맥 | 이벤트 유형을 enum으로 제한한다. JSON 문맥에는 민감정보를 넣지 않는다. |

### 7.6 `TravelRecordService`, `TravelRecordReportService`

```java
public interface TravelRecordService {
    TravelRecordResult create(long userId, CreateTravelRecordCommand command);
    TravelRecordDetailResult get(Long viewerUserId, long recordId);
    TravelRecordResult update(long userId, long recordId, UpdateTravelRecordCommand command);
    void replaceImages(long userId, long recordId, ReplaceRecordImagesCommand command);
    CommentResult addComment(long userId, long recordId, AddCommentCommand command);
    void deleteComment(long userId, long commentId);
    LikeResult like(long userId, long recordId);
    void unlike(long userId, long recordId);
    void recordShare(Long userId, long recordId, ShareRecordCommand command);
    void delete(long userId, long recordId);
}

public interface TravelRecordReportService {
    ReportResult report(long userId, long recordId, ReportRecordCommand command);
    ReportResult process(long adminUserId, long reportId, ProcessReportCommand command);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `CreateTravelRecordCommand` | `tripId`, 제목·본문·평점·공개 범위 | 완료 여행이며 작성자가 여행 소유자인지 확인한다. 여행당 기록 1건을 보장한다. |
| `ReplaceRecordImagesCommand` | 이미지 URL·대체 텍스트·대표 여부 목록 | 순서를 목록 순서로 정하고 대표 이미지는 최대 1개만 허용한다. |
| `AddCommentCommand` | 본문·선택적 `parentCommentId` | 답글이면 부모 댓글이 같은 기록에 속하는지 확인한다. |
| `ShareRecordCommand` | 공유 채널 | 비로그인 공유는 `userId`가 NULL일 수 있다. 채널은 DB enum과 맞춘다. |
| `ReportRecordCommand` | 신고 사유·상세 | 처리 중인 동일 사용자 신고를 서비스에서 차단한다. |
| `ProcessReportCommand` | 처리 상태·처리 메모 | ADMIN만 호출하며 `RESOLVED`, `REJECTED` 등 허용 상태 전이를 검증한다. |
| `TravelRecordDetailResult` | 기록·이미지·댓글·좋아요 수·작성자 요약 | 비공개 기록은 작성자 또는 관리자만 조회한다. |

### 7.7 티켓·예약·결제 Service

```java
public interface TicketService {
    PageResult<TicketProductResult> search(TicketSearchCondition condition, PageRequest page);
    InventoryHoldResult holdInventory(long productId, int quantity);
    void releaseInventory(long productId, int quantity, String reason);
    List<IssuedTicketResult> issue(long reservationId);
    TicketValidationResult validate(long validatorUserId, ValidateTicketCommand command);
}

public interface ReservationService {
    ReservationResult create(long userId, CreateReservationCommand command);
    ReservationDetailResult get(long userId, long reservationId);
    ReservationResult cancel(long userId, long reservationId,
                             CancelReservationCommand command);
    void expire(long reservationId);
}

public interface PaymentService {
    PaymentResult request(long userId, long reservationId, RequestPaymentCommand command);
    PaymentResult cancel(long userId, long paymentId, CancelPaymentCommand command);
}

public interface BookingQueueService {
    QueueTicketResult enqueue(long userId, long ticketProductId);
    QueueStatusResult getStatus(long userId, String queueToken);
    AdmissionResult admit(String queueToken);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `CreateReservationCommand` | 상품 ID·사용일·수량 | 판매 기간·사용 기간·사용자 최대 수량과 재고를 검증한다. 가격은 서버 상품값으로 계산한다. |
| `InventoryHoldResult` | 상품 ID·확보 수량·남은 수량·버전 | 조건부 UPDATE 성공 여부를 표현한다. 클라이언트가 재고 값을 결정하지 않는다. |
| `RequestPaymentCommand` | 결제 방식·멱등 키 | Mock 결제 입력이다. 결제 금액은 예약 총액을 사용한다. |
| `IssuedTicketResult` | 티켓 번호·발급 방식·유효기간·표시용 QR 값 | 검증 토큰 원문은 필요한 최초 응답에서만 제한적으로 다룬다. DB에는 해시를 저장한다. |
| `ValidateTicketCommand` | 검표 토큰·채널·장치 ID | 토큰 원문을 로그에 남기지 않는다. `ISSUED → USED` 조건부 갱신으로 중복 검표를 막는다. |
| `QueueTicketResult` | 대기열 토큰·초기 순번·만료시각 | Redis용 결과이며 예약 ID와 혼동하지 않는다. |

### 7.8 `AdminService`

```java
public interface AdminService {
    AdminDashboardResult getDashboard(long adminUserId);
    void recordAudit(long adminUserId, AdminAuditCommand command);
}
```

| DTO | 필드 예시 | 설명·검증 |
| --- | --- | --- |
| `AdminAuditCommand` | 작업 유형·대상 유형/ID·변경 전후 데이터·요청 ID·IP | 민감정보를 제거한 뒤 추가 전용 감사 로그로 저장한다. |
| `AdminDashboardResult` | 장소 동기화·미처리 신고·티켓 운영 집계 | 여러 도메인의 읽기 전용 집계 결과이며 Entity를 반환하지 않는다. |

### 7.9 공통 결과 형태

```java
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}

public enum UserRole { USER, ADMIN }
public enum TripStatus { DRAFT, CONFIRMED, COMPLETED, CANCELLED }
public enum RecommendationEventType {
    IMPRESSION, CLICK, FAVORITE, ADD_TO_TRIP, REMOVE_FROM_TRIP, DISMISS
}
```

## 8. 도메인 간 공개 계약

| 제공 도메인 | 공개 계약 | 사용 도메인 |
| --- | --- | --- |
| 회원 | 현재 회원 ID·상태·권한, 사용자 선호 | 전체 도메인, AI추천 |
| 장소 | 장소 상세·추천 후보 | 여행, AI추천, 티켓 |
| 여행 | 소유 여행·완료 여행 조회 | AI추천, 여행 기록 |
| AI추천 | 추천 결과·행동 기록 | 여행, 장소, 사용자 선호 |
| 여행 기록 | 공개 기록·신고 상태 | 마이페이지, 관리자 |
| 티켓 | 재고·예약·발급 상태 | 마이페이지, 관리자 |
| 관리자 | 권한·감사 기록 | 장소, 기록 신고, 티켓 |

다른 도메인에는 Entity가 아니라 필요한 필드만 가진 읽기 전용 Result/View를 제공한다.

## 9. 현재 화면 구현 상태

2026-07-24 기준으로 Thymeleaf 화면 21개와 페이지별 CSS·JavaScript 파일은 준비되어 있다. 다만 현재 구현은 화면 시안과 이동 흐름을 확인하기 위한 단계이며, 다음 항목은 아직 실제 기능이 아니다.

- `PageController` 한 클래스가 모든 화면 URL을 반환한다.
- 페이지 전용 JavaScript는 `body.dataset.pageReady`만 설정하고, 실제 API를 호출하지 않는다.
- 로그인·회원가입·관리자 폼은 `data-demo-form`, 버튼 동작은 `data-route`, `data-modal`, `data-toast` 기반의 공통 데모 처리다.
- 화면의 회원·장소·여행·추천·예약 데이터는 하드코딩되어 있다.
- `SecurityConfig`는 현재 모든 요청을 허용하므로 인증·소유권·ADMIN 접근 제어가 적용되지 않았다.
- `/trips/busan/**`, `/guide/places/haeundae`, `/booking/tickets/blueline`의 문자열은 화면 확인용 예시다. 실제 연동 시 DB ID 또는 팀에서 합의한 공개 식별자로 교체한다.

따라서 “화면 작업 완료”는 마크업·스타일 기준이며, 각 담당자는 화면을 다시 디자인하는 것이 아니라 입력 검증, API 호출, 로딩·빈 결과·오류 표시, 서버 데이터 렌더링을 구현한다.

## 10. 페이지·Controller·화면 기능 역할 분담

Page Controller는 Thymeleaf 화면 반환만 담당하고, JSON 요청은 도메인별 API Controller가 담당한다. 한 화면이 여러 도메인을 사용하더라도 HTML·페이지 JavaScript의 주 담당자는 한 명만 두고 다른 담당자는 API 계약으로 협업한다.

| 팀원 | 주 담당 화면 | Page Controller | 연결할 API Controller·Service | 화면 기능 구현 범위 |
| --- | --- | --- | --- | --- |
| 허민재 | `auth/login`, `auth/signup`, `mypage/mypage`, `admin/admin` | `AuthPageController`, `MemberPageController`, `AdminPageController` | `AuthApiController`, `MemberApiController`, `AdminApiController` → `AuthService`, `MemberService`, `AdminService` | 가입·중복 검사·로그인·로그아웃, 현재 회원/프로필/선호, 인증 상태별 헤더, ADMIN 접근 차단과 운영 집계 표시 |
| 정인길 | `guide/guide`, `guide/themes`, `guide/place-detail`; 2차 `booking/booking`, `booking/ticket`, `booking/hotels`, `booking/flights` | `GuidePageController`; 2차 `BookingCatalogPageController` | `PlaceApiController`, `FavoriteApiController`; 2차 `TicketApiController` → 장소·즐겨찾기·티켓 Service | 장소 검색·필터·상세·이미지·스타일·즐겨찾기·일정 추가 연결, 티켓 상품/재고 표시. 항공·숙소는 내부 결제가 아닌 외부 검색/링크 범위 |
| 홍유원 | `home/home`, `trips/basic`, `trips/style`, `trips/schedule`, `trips/map`; 2차 예약 처리 | `HomePageController`, `TripPageController` | `TripApiController`; 2차 `ReservationApiController`, `PaymentApiController` → 여행·예약·결제 Service | 여행 생성 단계의 입력 유지, 기간/인원/예산/스타일, 일정 CRUD·정렬·지도 표시, 소유권 오류 처리; 2차 예약 생성·취소·모의 결제 |
| 남현호 | `trips/record`와 마이페이지의 여행 기록·소셜 데이터 계약 | `TravelRecordPageController` | `TravelRecordApiController`, `TravelRecordReportApiController` → 여행 기록·신고 Service | 완료 여행 확인, 여행당 기록 1건, 본문·평점·공개 범위·이미지, 댓글·답글·좋아요·공유·신고 및 권한/빈 상태 표시 |
| 한성주 | `trips/recommendations`, `trips/optimize`, `guide/ai-guide`; 2차 `booking/queue` | `RecommendationPageController`, `AiGuidePageController`; 2차 `BookingQueuePageController` | `RecommendationApiController`, `AiTravelApiController`; 2차 `BookingQueueApiController` → AI·추천·대기열 Service | 추천 요청·결과·이유와 행동 이벤트, AI 일정 최적화 미리보기/적용, AI 채팅의 로딩·실패·재시도, 대기 순번·TTL 갱신 |

### 페이지별 완료 조건

| 화면 | 실제로 연결해야 할 기능 | 선행 계약 |
| --- | --- | --- |
| 로그인 | 이메일·비밀번호 검증, 인증 성공 처리, 원래 요청 화면으로 이동, 실패 메시지 | `AuthService`, Spring Security |
| 회원가입 | 입력 검증, 이메일·닉네임 중복 확인, 약관 동의, 가입 후 로그인 화면 이동 | `AuthService` |
| 홈 | 목적지·기간·인원을 여행 생성 단계로 전달 | `TripService` 입력 계약 |
| 여행 기본 정보·스타일 | 단계 간 입력 보존, 서버 검증, 여행 및 일차 생성 | `TripService` |
| 여행지 추천 | 추천 목록·점수·이유 표시, 노출/클릭/선택 이벤트, 선택 장소를 여행에 반영 | `RecommendationService`, `PlaceService`, `TripService` |
| 일정·지도 | 여행/일차/일정 조회, 추가·수정·삭제·정렬, 장소 검색, 지도 마커·동선 표시 | `TripService`, `PlaceService` |
| AI 최적화 | 현재 일정 요청, 변경 전후 비교, 적용 또는 취소, 실패 재시도 | `AiTravelService`, `TripService` |
| 여행 가이드·테마·장소 상세 | 검색·필터·페이지 처리, 이미지/스타일, 즐겨찾기, 일정 추가, 외부 링크 | `PlaceService`, `FavoriteService` |
| AI 가이드 | 대화 요청, 근거/추천 결과 표시, 전송 중 중복 방지, 오류·재시도 | `AiTravelService` |
| 여행 기록 | 완료 여행 검증, 기록 저장/수정, 이미지, 댓글·좋아요·공유·신고 | `TravelRecordService`, `TripService` |
| 마이페이지 | 프로필·선호, 내 여행·즐겨찾기·기록 조회; 2차 예약/티켓 조회 | 각 도메인의 읽기 전용 API |
| 관리자 | ADMIN 인가, 운영 집계; 2차 장소 동기화·신고·상품/재고·감사 조회 | `AdminService`와 각 운영 Service |
| 예약 허브·티켓 | 상품/재고 조회, 수량·사용일 선택, 예약·모의 결제·발권 결과 | 2차 booking Service |
| 항공·숙소 | 검색 조건 입력과 외부 제공자 결과/링크 표시만 지원 | 외부 검색 Adapter |
| 예약 대기열 | 순번·예상 시간·만료 상태 주기 조회, 입장 토큰으로 예약 화면 이동 | 2차 `BookingQueueService` |

`mypage/mypage.html`과 `admin/admin.html`처럼 여러 도메인의 결과를 모으는 화면은 페이지 주 담당자가 파일을 소유한다. 다른 담당자는 해당 화면 파일을 직접 수정하기보다 자신의 조회 API와 응답 DTO를 제공한다.

## 11. Controller 구성과 충돌 방지 원칙

권장 구조:

```text
presentation/
├── page/
│   ├── HomePageController.java
│   ├── AuthPageController.java
│   ├── MemberPageController.java
│   ├── TripPageController.java
│   ├── TravelRecordPageController.java
│   ├── GuidePageController.java
│   ├── RecommendationPageController.java
│   ├── AiGuidePageController.java
│   ├── BookingCatalogPageController.java       # 2차
│   ├── BookingQueuePageController.java         # 2차
│   └── AdminPageController.java
└── api/                                        # 또는 각 도메인의 controller 패키지
    ├── member/
    ├── place/
    ├── trip/
    ├── recommendation/
    ├── record/
    ├── booking/
    └── admin/
```

1. 기존 `PageController`는 위 Page Controller로 한 번에 분리하고, 동일 URL 매핑을 두 클래스에 중복 등록하지 않는다.
2. Page Controller는 화면 이름과 최소 View Model만 반환한다. 데이터 변경, Repository 호출, 외부 API 호출은 넣지 않는다.
3. API Controller는 입력 DTO 검증, 현재 사용자 조회, Service 호출, HTTP 상태·응답 변환까지만 담당한다.
4. 인증된 사용자 ID를 요청 본문이나 쿼리에서 받지 않는다. `CurrentUserProvider`에서 가져온다.
5. URL은 `/api/v1` 아래 복수 명사와 리소스 중심으로 합의한다. 예: `/api/v1/trips/{tripId}/itinerary-items`.
6. 화면 JavaScript는 자기 도메인의 `static/js/pages/{domain}` 파일에서 구현하고 인라인 스크립트를 추가하지 않는다.
7. `static/js/common/api.js`, `navigation.js`, `modal.js`, 공통 fragment는 허민재가 초기 통합 창구를 맡는다. 변경이 필요하면 화면 담당자가 요구사항을 전달하고 공통 파일을 동시에 수정하지 않는다.
8. 모달의 확인 버튼을 실제 기능으로 연결할 때 공통 `data-complete` 데모 처리와 중복 실행되지 않도록 해당 데모 속성을 제거하거나 기능별 이벤트로 교체한다.
9. 각 화면은 정상 상태뿐 아니라 로딩, 빈 데이터, 잘못된 식별자(404), 미인증(401), 권한 없음(403), 검증 실패(400)를 처리한다.
10. Page Controller 테스트는 URL·뷰 이름·접근 권한을, API Controller 테스트는 검증·상태 코드·Service 호출을 검증한다.

## 12. Controller·화면 연동 템플릿

Page Controller와 API Controller를 분리하는 최소 예시는 다음과 같다.

```java
@Controller
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripPageController {

    @GetMapping("/{tripId}/schedule")
    public String schedule(@PathVariable long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        return "trips/schedule";
    }
}

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripApiController {

    private final TripService tripService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> get(@PathVariable long tripId) {
        long userId = currentUserProvider.requireUserId();
        TripDetailResult result = tripService.get(userId, tripId);
        return ApiResponse.ok(TripDetailResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TripResponse>> create(
            @Valid @RequestBody CreateTripRequest request) {
        long userId = currentUserProvider.requireUserId();
        TripResult result = tripService.create(userId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(TripResponse.from(result)));
    }
}
```

```javascript
// static/js/pages/trips/schedule.js
// 템플릿의 data-trip-id 값을 읽고 화면 진입 시 조회한다.
document.addEventListener("DOMContentLoaded", async () => {
  const tripId = document.body.dataset.tripId;
  try {
    setLoading(true);
    const trip = await window.AllMyTripsApi.get(`/api/v1/trips/${tripId}`);
    renderSchedule(trip.data);
  } catch (error) {
    renderPageError(error);
  } finally {
    setLoading(false);
  }
});
```

요청 DTO는 HTTP 형식과 Bean Validation을, Command는 Service의 유스케이스 의미를 담당한다. 둘의 필드가 같더라도 Controller에서 `request.toCommand()`로 명시적으로 변환하여 웹 계층 변경이 Service 계약으로 번지지 않게 한다.

## 13. 1차 완료 기준

- 회원가입·로그인·로그아웃과 현재 회원 조회가 동작한다.
- 권한 없는 사용자의 다른 회원 여행·기록 접근이 차단된다.
- 장소 검색·상세·즐겨찾기가 동작한다.
- 여행 생성 시 기간에 맞는 일차가 생성된다.
- 일정 항목 CRUD와 순서 변경이 동작한다.
- AI 요청 성공·실패 이력이 저장되고 추천 행동이 기록된다.
- 완료 여행 소유자만 여행 기록을 1건 작성할 수 있다.
- 댓글·좋아요·공유·신고의 유일성 및 상태 규칙을 지킨다.
- 각 Service 구현에 단위 테스트 또는 통합 테스트가 존재한다.
