# TRIP-00 여행 Service 구조 점검

> 담당: 남현호  
> 목적: 여행 CRUD를 구현하는 Issue가 아니라, 후속 구현 전에 여행·일차·세부 일정의 책임과 트랜잭션·소유권 규칙을 합의하는 문서다.

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
| `TripService` | 여행 생성·단건 조회·회원별 목록·수정·soft delete 제공 | 로그인 사용자 소유권 검사, 생성 시 일차 자동 생성, 상태 전환 규칙 필요 |
| `TripDayService` | 일차 생성·여행별 조회·수정·물리 삭제 제공 | 여행 기간 안의 날짜인지 검사하고 외부 직접 호출 제한 |
| `ItineraryItemService` | 일정 생성·일차별 조회·수정·물리 삭제 제공 | 여행 소유권, 일차 소속, 시간·정렬 순서 검증 필요 |
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

일차 수 계산은 `종료일 - 시작일 + 1`이다. 허용 가능한 최대 여행 기간은 정책이 정해진 뒤 상수로 제한한다.

## 5. 소유권 검사

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

보안을 위해 다른 사용자 여행의 존재 여부를 노출하지 않도록 `NOT_FOUND`로 통일할지는 공통 예외 정책 담당자와 합의한다.

## 6. 삭제 정책

### 1차 구현 권장안

- 사용자가 여행을 삭제하면 `trips.status = 'CANCELLED'`, `deleted_at`을 기록한다.
- `trip_days`, `itinerary_items`는 즉시 물리 삭제하지 않는다.
- 삭제된 여행의 일차와 일정은 API에서 조회·수정할 수 없게 한다.
- 복구 기능은 1차 범위에서 제외한다.

### 물리 삭제가 필요한 경우

보관 기간이 지난 데이터에 대한 관리자 정리 작업을 별도 Issue로 만든다. `trips`를 물리 삭제하면 DB의 `ON DELETE CASCADE`에 따라 `trip_days`, `itinerary_items`, `trip_travel_styles`, `trip_share_links`도 함께 삭제된다.

## 7. 홍유원 화면과 연결할 여행 기본정보 계약

현재 `templates/trips/basic.html`에 실제로 존재하는 입력 항목을 기준으로 한다.

| 화면 항목 | 요청 필드 예시 | DB 반영 | 설명 |
| --- | --- | --- | --- |
| 여행지 | `destinationName` | `trips.destination_name` | 필수, 공백 불가 |
| 시작일 | `startDate` | `trips.start_date` | 필수, `yyyy-MM-dd` |
| 종료일 | `endDate` | `trips.end_date` | 필수, 시작일보다 빠를 수 없음 |
| 동행자 유형 | `companionType` | `trips.companion_type` | 화면 표시값을 DB enum 코드로 변환 |
| 예상 예산 | `budgetAmount` | `trips.budget_amount` | 0 이상, 통화는 우선 `KRW` |

화면에 아직 없는 `title`, `companionCount`는 서버 기본값 또는 다음 단계 입력 정책을 팀장과 합의해야 한다. 현재 DB에서 `title`, `companionCount`는 필수이므로 누락한 채 저장할 수 없다.

여행 이름과 여행 스타일은 현재 기본정보 화면 입력값이 아니다. 여행 스타일은 다음 화면에서 수집하고 `trip_travel_styles`에 저장한다.

권장 요청 예시:

```java
public record CreateTripBasicCommand(
        String destinationName,
        LocalDate startDate,
        LocalDate endDate,
        CompanionType companionType,
        BigDecimal budgetAmount
) {}
```

이 Command에는 `userId`, `status`, `source`, `currencyCode`, `deletedAt`을 받지 않는다. 해당 값은 인증 정보와 서버 정책으로 결정한다.

## 8. 후속 공개 Service 계약 초안

메서드명과 DTO는 API 설계 과정에서 바뀔 수 있다.

```java
public interface TripService {
    TripResult create(long userId, CreateTripCommand command);
    TripDetailResult get(long userId, long tripId);
    List<TripSummaryResult> getMyTrips(long userId);
    TripResult update(long userId, long tripId, UpdateTripCommand command);
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

`TripResult`에는 화면에 필요한 공개 정보만 반환하고, `deletedAt` 같은 내부 관리값은 기본 응답에서 제외한다.

## 9. 후속 Issue 분리안

| 순서 | Issue | 구현 범위 |
| ---: | --- | --- |
| 1 | `TRIP-02 여행 생성 API` | 소유 회원 확인, 기본정보 저장, 기간별 일차 자동 생성, 트랜잭션 테스트 |
| 2 | `TRIP-03 내 여행 조회 API` | 본인 목록·상세·일차·일정 조회, 삭제 여행 제외 |
| 3 | `TRIP-04 여행 기본정보 수정·삭제` | 소유권, 기간 변경 정책, soft delete |
| 4 | `TRIP-05 세부 일정 CRUD` | 일정 추가·수정·삭제, 시간·비용·장소 검증 |
| 5 | `TRIP-06 일정 순서 변경` | 전체 item ID 검증, 중복·누락 차단, 일괄 정렬 |
| 6 | `TRIP-07 여행 스타일 저장` | 선택 스타일 우선순위와 `trip_travel_styles` 저장 |

## 10. 팀 합의가 필요한 항목

- 기본정보 화면에 없는 여행 제목의 생성 규칙
- 동행자 화면 값 중 `부모님`, `아이와 함께`를 DB `companion_type` 코드에 매핑하는 규칙
- 기본 동행 인원 수와 화면에서 인원 입력을 받을 시점
- 여행 최대 기간
- 여행 기간 수정 시 기존 일차·일정을 유지할지, 삭제할지, 이동할지
- 다른 사용자 여행 접근 시 `403`과 `404` 중 사용할 공통 정책
- 삭제 여행 보관 기간과 물리 삭제 여부

위 항목이 정해지기 전에는 기존 DTO·DB enum·Controller API를 임의 변경하지 않는다.

## 11. TRIP-00 완료 체크리스트

- [x] 여행 DTO·DAO·Service·Mapper 확인
- [x] `trips`, `trip_days`, `itinerary_items` DB 관계 확인
- [x] 여행 생성 트랜잭션 범위 정리
- [x] 여행 삭제 시 하위 데이터 처리 정리
- [x] 로그인 사용자 소유권 검사 방법 정리
- [x] 기본정보 화면 요청값 정리
- [x] 후속 구현 Issue 분리안 작성
- [ ] 팀장과 미결정 정책 합의
