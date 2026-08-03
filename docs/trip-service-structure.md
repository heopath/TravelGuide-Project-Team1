# TRIP-00 여행 Service 구조 점검

> 담당: 남현호 · 백업: 허민재
> 목적: 여행 CRUD를 구현하는 Issue가 아니라, 후속 구현 전에 여행·일차·세부 일정의 책임과 트랜잭션·소유권 규칙을 합의하는 문서다.
> 상태: 팀장 검토 완료. 이 문서의 1차 구현 정책을 후속 여행 API의 기준으로 사용한다.

## 1. 이번 Issue의 결론

여행 도메인의 Aggregate Root는 `Trip`으로 본다.

```text
Trip
└── TripDay (1일차, 2일차, ...)
    └── ItineraryItem (장소 방문, 식사, 이동, 메모, ...)
```

- 외부 계층에는 `TripService`를 여행 도메인의 대표 진입점으로 제공한다.
- 현재 존재하는 `TripDayService`, `ItineraryItemService`는 당장 삭제하지 않는다.
- 후속 구현에서는 두 Service를 내부 협력 Service로 사용하거나 `TripService` 구현 내부로 단계적으로 합칠 수 있다.
- Controller가 `TripDayService`, `ItineraryItemService`를 직접 호출해 소유권 검사를 우회하지 않게 한다.
- 모든 변경 작업은 로그인 사용자 ID와 여행 ID를 함께 받아 소유권을 확인한다.

즉, 테이블마다 Service를 공개하는 구조보다 `TripService`가 여행 전체의 규칙을 통제하는 구조를 목표로 한다.

## 2. 현재 코드 점검 결과

| 구분 | 현재 상태 | 후속 구현 시 필요한 보완 |
| --- | --- | --- |
| `TripService` | 여행 생성·소유자 기준 조회·회원별 목록·수정·soft delete 제공 | 생성 시 일차 자동 생성, 기간 수정, 상태 전환 규칙 필요 |
| `TripDayService` | 소유권 확인 후 일차 생성·조회·수정·물리 삭제 제공 | 여행 기간 안의 날짜인지 검사하고 외부 직접 호출 제한 |
| `ItineraryItemService` | 소유권·일차 소속 확인 후 일정 생성·조회·수정·물리 삭제 제공 | 시간 검증과 정렬 순서 일괄 변경 필요 |
| DTO | DB 컬럼과 거의 1:1인 가변 DTO 사용 | API 요청 DTO와 Service Command/Result를 분리하는 것이 안전 |
| Mapper | 기본 CRUD SQL 존재 | 수정·삭제 조건에 `user_id`가 없어 Service 소유권 검사가 필수 |
| DB | 기간·상태·시간·정렬 순서 CHECK와 하위 테이블 CASCADE 존재 | DB 제약과 같은 규칙을 Service에서도 사용자 친화적 메시지로 검증 |

### 확인된 중요 사항

1. `trips → trip_days → itinerary_items` 외래 키에는 `ON DELETE CASCADE`가 설정돼 있다.
2. 현재 여행 삭제는 `trips.deleted_at`을 기록하는 soft delete이므로 DB CASCADE가 실행되지 않는다.
3. 따라서 여행을 soft delete해도 하위 일차와 일정 행은 DB에 남는다. 일반 조회에서 삭제된 여행의 하위 데이터를 노출하지 않도록 반드시 여행 소유권과 삭제 여부를 함께 확인해야 한다.
4. 여행을 물리 삭제하는 관리자 정리 기능을 나중에 추가하면 그때 DB CASCADE로 하위 데이터가 함께 삭제된다.
5. `TripDayMapper`, `ItineraryItemMapper`의 수정·삭제 SQL은 식별자만 조건으로 사용하므로 Controller 입력 ID만 믿고 호출하면 다른 사용자의 데이터를 변경할 위험이 있다.

## 3. Service 책임

### `TripService` — 외부 공개 진입점

- 로그인 사용자의 여행 생성
- 여행 기간만큼 `TripDay` 자동 생성
- 본인 여행 단건·목록·전체 일정 조회
- 여행 기본정보 수정
- 여행 상태 변경
- 여행 soft delete
- 여행 일차와 세부 일정 추가·수정·삭제·정렬
- 여행·일차·일정의 소유관계 검증

### `TripDayService` — 내부 협력 책임

- 시작일부터 종료일까지 일차 생성
- `dayNumber`와 `tripDate` 계산
- 변경된 여행 기간에 맞춰 일차 조정
- 여행 기간을 벗어난 일차 차단

### `ItineraryItemService` — 내부 협력 책임

- 특정 일차의 일정 목록 조회
- 일정 추가·수정·삭제
- 시작·종료 시각 검증
- 같은 일차 안의 `sortOrder` 관리
- 장소가 없어도 메모·이동·식사 일정을 만들 수 있도록 처리

현재 세 클래스는 모두 구현 클래스다. 구조가 안정되면 외부 계약인 `TripService` 인터페이스와 구현체 `TripServiceImpl`을 분리하는 방안을 후속 Issue에서 검토한다.

## 4. 여행 생성 트랜잭션

여행 기본정보와 일차 생성은 하나의 트랜잭션으로 처리한다.

```text
1. 인증된 회원 ID 확인
2. 회원이 활성 상태인지 확인
3. 요청값 검증
4. trips에 여행 기본정보 저장
5. 시작일~종료일을 순회하며 trip_days 생성
6. 모두 성공하면 commit
7. 하나라도 실패하면 여행과 일차를 전부 rollback
```

예를 들어 2026-08-14부터 2026-08-17까지 입력하면 다음 네 행을 만든다.

| dayNumber | tripDate |
| ---: | --- |
| 1 | 2026-08-14 |
| 2 | 2026-08-15 |
| 3 | 2026-08-16 |
| 4 | 2026-08-17 |

일차 수 계산은 `종료일 - 시작일 + 1`이다. 1차에서는 시작일과 종료일을 포함해 최대 30일까지 허용한다. 31일 이상인 요청은 `tripDAO.insert()`를 호출하기 전에 Service에서 거절한다.

## 5. 여행 수정 트랜잭션

1차에서는 여행 기간을 수정할 때 기존 일정을 서버가 임의로 삭제하거나 이동하지 않는다. 기간 단축 또는 이동으로 제외되는 `TripDay`에 일정이 있으면 전체 변경을 거절하고 `409 TRIP_PERIOD_CONFLICT`를 반환한다.

### 이 정책을 선택한 이유

- `TripDay`는 날짜 묶음이고 `ItineraryItem`은 사용자가 직접 만든 장소·식사·이동 일정이므로, 기간 변경만으로 일정을 자동 삭제하면 복구하기 어려운 데이터 손실이 발생한다.
- 서버가 가장 가까운 날짜로 자동 이동하면 사용자의 이동 시간·예약 시간·숙박 계획과 맞지 않을 수 있다.
- 충돌을 명확한 오류로 반환하면 사용자가 일정 화면에서 직접 이동·삭제한 뒤 다시 시도할 수 있다.
- 여행 기간, 일차, 일정 변경을 한 트랜잭션으로 묶으면 일부만 반영돼 여행 기간과 실제 일차가 달라지는 상태를 막을 수 있다.

### 1차 처리 흐름

```text
1. 로그인 사용자와 여행 소유권 확인
2. 여행 행과 관련 TripDay를 잠금
3. 새 기간이 1~30일인지 검증
4. 기간 연장이면 새 날짜의 TripDay 생성
5. 기간 단축·이동이면 제외되는 TripDay의 일정 존재 여부 확인
6. 일정이 하나라도 있으면 409 TRIP_PERIOD_CONFLICT를 반환하고 rollback
7. 일정이 없으면 제외되는 빈 TripDay 삭제
8. 새로 포함되는 날짜의 TripDay 생성
9. 남은 TripDay를 날짜순으로 dayNumber 재정렬하고 trips 기간 수정
10. 모두 성공하면 commit, 하나라도 실패하면 rollback
```

예를 들어 8월 14~17일 여행을 8월 15~18일로 변경할 때 14일에 일정이 있으면 아무 데이터도 변경하지 않고 `409 TRIP_PERIOD_CONFLICT`를 반환한다. 사용자는 일정 화면에서 해당 일정을 직접 이동하거나 삭제한 후 기간 변경을 다시 요청한다. 14일이 비어 있으면 해당 `TripDay`를 삭제하고 18일 `TripDay`를 생성한 뒤 순번을 재정렬한다.

자동 일정 이동·자동 삭제·기간 변경 영향 미리보기는 1차 범위에서 제외하고 후속 Issue로 구현한다. 후속 단계에서도 사용자 확인 없이 기존 일정을 삭제하거나 임의 날짜로 이동하지 않는 원칙은 유지한다.

제목·예산·동행 유형처럼 기간과 무관한 정보만 수정할 때는 `trips` 한 행만 변경한다. `COMPLETED`, `CANCELLED` 여행의 기간 변경은 허용하지 않는다.

## 6. 일정 항목 순서 변경 트랜잭션

클라이언트는 특정 일차의 일정 ID 전체를 원하는 순서대로 전달한다.

```java
public record ReorderItineraryCommand(List<Long> itemIds) {}
```

처리 규칙:

```text
1. 여행과 일차 행을 조회하고 로그인 사용자 소유권 확인
2. 해당 일차의 일정 행을 SELECT ... FOR UPDATE로 잠금
3. 요청 ID의 중복 여부 확인
4. 요청 ID 집합과 DB의 전체 일정 ID 집합이 같은지 확인
5. 다른 일차의 ID, 누락된 ID, 존재하지 않는 ID가 있으면 전체 거절
6. 기존 최댓값보다 큰 임시 sortOrder 영역으로 모든 항목 이동
7. 요청 배열 순서대로 0부터 최종 sortOrder 부여
8. 모두 성공하면 commit, 하나라도 실패하면 rollback
```

현재 DB에는 `(trip_day_id, sort_order)` UNIQUE 제약이 있으므로 기존 순번을 바로 교환하면 중간 단계에서 중복될 수 있다. 따라서 두 단계 UPDATE를 사용한다.

```text
현재: A=0, B=1, C=2
임시: A=4, B=5, C=6
최종: C=0, A=1, B=2
```

임시 시작값은 `현재 max(sort_order) + 1`로 계산하고 요청 순서 인덱스를 더한다. `sort_order`가 PostgreSQL `SMALLINT` 범위를 넘을 가능성이 있으면 변경을 거절한다. 1차에서는 한 일차에 일정 항목을 최대 100개까지 허용하며 일정 추가와 순서 변경에서 같은 상한을 검증한다.

## 7. 소유권 검사

모든 변경 메서드는 클라이언트가 보낸 `userId`를 신뢰하지 않는다. 인증 계층에서 얻은 사용자 ID를 사용한다.

권장 조회 계약:

```java
TripDTO getOwnedTrip(long userId, long tripId);
```

검사 순서:

```text
1. deleted_at IS NULL인 여행 조회
2. 여행이 없으면 NOT_FOUND
3. trips.user_id와 로그인 사용자 ID 비교
4. 다르면 접근 권한이 없음을 반환
5. 일차·일정이 해당 여행 아래에 속하는지 확인
6. 확인 후 변경 SQL 실행
```

로그인하지 않은 사용자는 `401 UNAUTHORIZED`를 반환한다. 존재하지 않거나 삭제됐거나 다른 사용자가 소유한 여행은 존재 여부를 노출하지 않도록 모두 `404 TRIP_NOT_FOUND`로 통일한다.

## 8. 삭제 트랜잭션과 정책

### 1차 구현 확정안

- 사용자가 여행을 삭제하면 `trips.status = 'CANCELLED'`, `deleted_at`을 기록한다.
- `trip_days`, `itinerary_items`는 즉시 물리 삭제하지 않는다.
- 삭제된 여행의 일차와 일정은 API에서 조회·수정할 수 없게 한다.
- 복구 기능은 1차 범위에서 제외한다.
- 소유권 확인과 soft delete UPDATE를 하나의 트랜잭션에서 처리한다.
- 이미 삭제됐거나 취소된 여행에 같은 요청이 다시 들어오면 `404 TRIP_NOT_FOUND`를 반환한다.

### 물리 삭제가 필요한 경우

`trips`를 물리 삭제하면 DB의 `ON DELETE CASCADE`에 따라 `trip_days`, `itinerary_items`, `trip_travel_styles`, `trip_share_links`도 함께 삭제돼 복구가 어렵다. 물리 삭제·복구·휴지통은 1차 범위에서 제외하고 후속 Issue로 분리한다.

## 9. 홍유원 화면과 연결할 여행 기본정보 계약

현재 `templates/trips/basic.html`에 실제로 존재하는 입력 항목을 기준으로 한다.

| 화면 항목 | 요청 필드 예시 | DB 반영 | 설명 |
| --- | --- | --- | --- |
| 여행지 | `destinationName` | `trips.destination_name` | 필수, 공백 불가 |
| 시작일 | `startDate` | `trips.start_date` | 필수, `yyyy-MM-dd` |
| 종료일 | `endDate` | `trips.end_date` | 필수, 시작일보다 빠를 수 없음 |
| 동행자 유형 | `companionType` | `trips.companion_type` | 화면 표시값을 DB enum 코드로 변환 |
| 동행 인원 | `companionCount` | `trips.companion_count` | 사용자가 직접 입력, 1~20명 |
| 예상 예산 | `budgetAmount` | `trips.budget_amount` | 0 이상, 통화는 우선 `KRW` |

`title`이 없으면 서버에서 `목적지명 + 여행` 형식으로 생성한다. 목적지 변경 시 현재 제목이 이전 목적지로 만든 자동 생성 제목과 정확히 같을 때만 새 목적지명에 맞춰 제목도 바꾼다. 사용자가 제목을 직접 수정했다면 목적지가 바뀌어도 제목을 유지한다.

동행 인원은 사용자가 직접 입력하고 1~20명만 허용한다. 화면 초기값은 `SOLO`이면 1명, 그 외 유형이면 2명으로 제안하되 사용자가 바꿀 수 있어야 한다. `SOLO`의 최종 저장값은 반드시 1명이어야 한다.

여행 이름과 여행 스타일은 현재 기본정보 화면 입력값이 아니다. 여행 스타일은 다음 화면에서 수집하고 `trip_travel_styles`에 저장한다.

화면 초안과 서버 DTO는 분리한다. 1차 API DTO 이름은 다음과 같이 사용한다.

| DTO | 역할 |
| --- | --- |
| `TripCreateRequest` | 여행 생성 Controller 요청값 |
| `TripUpdateRequest` | 제목·목적지·동행·예산 등 기본정보 수정 요청값 |
| `TripPeriodUpdateRequest` | 시작일·종료일 변경 요청값 |
| `TripResponse` | 여행 조회 API 응답값 |
| `TripCreateResult` | 생성된 여행 ID와 생성된 일차 수를 Service에서 반환 |

요청 DTO 예시:

```java
public record TripCreateRequest(
        String title,
        String destinationName,
        LocalDate startDate,
        LocalDate endDate,
        CompanionType companionType,
        Integer companionCount,
        BigDecimal budgetAmount
) {}
```

`userId`, `status`, `source`, `createdAt`, `updatedAt`, `deletedAt`은 요청에서 받지 않고 인증 정보와 서버 정책으로 결정한다.

현재 TRIP-01 화면은 API 호출 전 단계이므로 다음 객체를 `sessionStorage`에 저장해 다음 화면으로 넘긴다.

```json
{
  "destinationName": "부산",
  "destinationLabel": "부산 · 해운대구",
  "startDate": "2026-08-14",
  "endDate": "2026-08-17",
  "companionType": "COUPLE",
  "companionLabel": "연인",
  "companionCount": 2,
  "budgetAmount": 300000,
  "currencyCode": "KRW"
}
```

`destinationLabel`, `companionLabel`은 화면 표시용 값이고 DB 저장 계약에는 포함하지 않는다. `userId`는 화면 객체에서 받지 않고 로그인 정보에서 얻는다. 후속 여행 생성 API가 만들어지면 화면 객체를 서버용 `TripCreateRequest`로 변환한다.

### 남현호·홍유원 담당 경계

| 구분 | 남현호 — TRIP-00 | 홍유원 — TRIP-01 |
| --- | --- | --- |
| 주 책임 | 여행 Service 책임·트랜잭션·소유권·입출력 계약 정리 | 여행 기본정보 화면 입력·검증·다음 단계 이동 구현 |
| 담당 파일 | `docs/trip-service-structure.md` | `templates/trips/basic.html`, `static/js/pages/trips/basic.js` |
| 데이터 처리 | 서버가 받아야 할 필드와 서버 결정값 정의 | 화면 입력을 JavaScript 객체 또는 mock/sessionStorage로 유지 |
| 오류 처리 | Service에서 검증할 규칙과 공통 오류 조건 정의 | 누락 입력과 잘못된 날짜를 사용자에게 안내 |
| 하지 않는 일 | TRIP-01의 HTML/CSS/JavaScript를 임의 수정하지 않음 | Controller·Service·Mapper·DTO·DDL을 임의 수정하지 않음 |
| 계약 변경 | 팀장·홍유원에게 검토 요청 후 문서 수정 | 필요한 필드가 다르면 구현 전 남현호·팀장에게 확인 |

남현호의 이번 Issue는 설계 문서 완료까지이며 전체 여행 CRUD 구현은 후속 Issue에서 별도 배정한다.

## 10. 후속 공개 Service 계약 초안

메서드명과 DTO는 API 설계 과정에서 바뀔 수 있다.

```java
public interface TripService {
    TripCreateResult create(long userId, TripCreateRequest request);
    TripResponse get(long userId, long tripId);
    List<TripResponse> getMyTrips(long userId);
    TripResponse update(long userId, long tripId, TripUpdateRequest request);
    TripResponse updatePeriod(long userId, long tripId, TripPeriodUpdateRequest request);
    void delete(long userId, long tripId);

    ItineraryItemResult addItem(
            long userId,
            long tripId,
            long tripDayId,
            AddItineraryItemCommand command
    );

    ItineraryItemResult updateItem(
            long userId,
            long tripId,
            long itemId,
            UpdateItineraryItemCommand command
    );

    void deleteItem(long userId, long tripId, long itemId);
    List<ItineraryItemResult> reorderItems(
            long userId,
            long tripId,
            long tripDayId,
            ReorderItineraryCommand command
    );
}
```

`TripResponse`에는 화면에 필요한 공개 정보만 반환하고, `deletedAt` 같은 내부 관리값은 기본 응답에서 제외한다. 구현이 계층별 DTO 분리를 채택하면 Controller의 `Request`를 Service `Command`로 변환할 수 있으나 외부 API 필드와 검증 규칙은 이 계약을 유지한다.

## 11. 후속 Issue 분리안

| 순서 | Issue | 구현 범위 |
| ---: | --- | --- |
| 1 | `TRIP-02 여행 생성 API` | 소유 회원 확인, 기본정보 저장, 기간별 일차 자동 생성, 트랜잭션 테스트 |
| 2 | `TRIP-03 내 여행 조회 API` | 본인 목록·상세·일차·일정 조회, 삭제 여행 제외 |
| 3 | `TRIP-04 여행 기본정보 수정·삭제` | 소유권, 기간 변경 정책, soft delete |
| 4 | `TRIP-05 세부 일정 CRUD` | 일정 추가·수정·삭제, 시간·비용·장소 검증 |
| 5 | `TRIP-06 일정 순서 변경` | 전체 item ID 검증, 중복·누락 차단, 일괄 정렬 |
| 6 | `TRIP-07 여행 스타일 저장` | 선택 스타일 우선순위와 `trip_travel_styles` 저장 |

## 12. 팀장 검토 완료 정책

| 항목 | 1차 확정 정책 | 후속 범위 |
| --- | --- | --- |
| 여행 제목 | 제목이 없으면 서버가 `목적지명 + 여행`으로 생성한다. 목적지 변경 시 기존 제목이 자동 생성 제목일 때만 제목도 변경한다. | 사용자 제목 편집 UX 고도화 |
| 동행 유형 | 현재 DB enum을 유지하고 `가족·부모님·아이와 함께`는 `FAMILY`로 저장한다. | 세부 동행 유형 enum 또는 컬럼 검토 |
| 동행 인원 | 사용자가 직접 입력하며 1~20명만 허용한다. `SOLO`는 1명, 그 외 유형의 화면 초기값은 2명이다. | 없음 |
| 여행 기간 | 시작일과 종료일을 포함해 최대 30일이다. 31일 이상은 저장 전에 거절한다. | 장기 체류 정책 검토 |
| 일차별 일정 | 최대 100개까지 허용한다. | 사용성·부하 결과에 따른 조정 |
| 인증·소유권 | 비로그인은 `401 UNAUTHORIZED`, 존재하지 않음·삭제됨·타인 소유는 `404 TRIP_NOT_FOUND`다. | 관리자 API 별도 정책 |
| 여행 삭제 | `status=CANCELLED`, `deleted_at`을 기록하는 soft delete만 지원한다. | 물리 삭제·복구·휴지통 |
| 기간 변경 충돌 | 일정이 있는 제외 날짜가 있으면 `409 TRIP_PERIOD_CONFLICT`로 전체 거절한다. | 자동 이동·자동 삭제가 아닌 사용자 선택형 이동·삭제와 영향 미리보기 |

현재 페이지와 DB의 동행 유형 표현이 완전히 같지 않다는 점은 알려진 제약이다. 1차에서는 스키마를 바꾸지 않고 `FAMILY`로 통합하며, 세부 유형이 추천에 필요해지는 시점에 DB migration·DTO·JavaScript·seed를 함께 변경한다.

물리 삭제는 하위 일차·일정과 관련 참조를 연쇄 삭제하므로 1차에서 사용하지 않는다. 후속 정책은 휴지통과 복구 기간, 보관 기간 후 익명화, 관리자 승인 후 배치 삭제를 비교해 별도 Issue에서 결정한다.

Controller·Service·Mapper·DTO·DDL 계약을 변경해야 하면 담당자와 팀장에게 먼저 공유한다.

## 13. 후속 구현 PR 필수 테스트

- 최대 30일까지 여행 생성 성공
- 31일 이상 요청 시 저장 전에 거절
- 기간 초과 시 `tripDAO.insert()` 미호출
- 타인 여행 접근 시 `404 TRIP_NOT_FOUND`
- 제외 날짜에 충돌 일정이 있는 기간 단축 요청 시 `409 TRIP_PERIOD_CONFLICT`
- 기간 변경 충돌 시 여행·일차·일정 데이터가 변경되지 않음
- 기간 연장 시 새 날짜의 `TripDay` 생성 및 날짜순 `dayNumber` 정렬
- 일정이 없는 날짜를 제외할 때 빈 `TripDay` 삭제 및 전체 트랜잭션 성공

## 14. TRIP-00 완료 체크리스트

- [x] 여행 DTO·DAO·Service·Mapper 확인
- [x] `trips`, `trip_days`, `itinerary_items` DB 관계 확인
- [x] 여행 생성 트랜잭션 범위 정리
- [x] 여행 수정 트랜잭션과 일정 보존 정책 정리
- [x] 여행 삭제 시 하위 데이터 처리 정리
- [x] 일정 항목 순서 변경 트랜잭션 정리
- [x] 로그인 사용자 소유권 검사 방법 정리
- [x] 기본정보 화면 요청값 정리
- [x] TRIP-01 화면 초안 출력값과 서버 계약 구분
- [x] 남현호·홍유원 담당 경계 정리
- [x] 후속 구현 Issue 분리안 작성
- [x] 팀장 검토 의견 반영
- [x] 1차 구현 정책 확정
- [x] 후속 구현 PR 필수 테스트 정의

## 15. 문서 확정 후 구현 인수인계

문서 PR은 `develop`에 병합한 뒤 로컬 `develop`을 최신 상태로 갱신한다. 문서 전용 브랜치에서 Service 구현을 계속하면 문서 PR과 구현 PR의 이력이 섞이므로, 실제 백엔드 구현은 최신 `develop`에서 새 기능 브랜치를 만들어 시작한다.

```text
feature/trip-service-boundary 문서 PR 승인·병합
→ develop checkout
→ origin/develop pull
→ 후속 여행 API용 feature 브랜치 생성
→ Service·DTO·Mapper 구현과 테스트
→ develop 대상 구현 PR 생성
```

구현 브랜치명은 후속 Issue 번호가 확정되면 `feature/{issue-number}-trip-api` 형태를 우선 사용한다. 후속 구현 PR은 13절의 필수 테스트를 포함하고, 이 문서의 확정 정책을 변경해야 할 경우 코드보다 먼저 담당자와 팀장에게 공유한다.
