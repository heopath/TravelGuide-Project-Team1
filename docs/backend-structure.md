# All My Trips 백엔드 기본 구조

## 적용 범위

초기 화면 라우팅은 유지하고, 핵심 기능부터 연결할 수 있도록 회원·장소·여행·일자·일정 항목의 MyBatis 계층과 REST Controller를 추가했다.

```text
domain
├── user
│   ├── dto
│   ├── mapper
│   ├── dao
│   ├── service
│   └── controller
├── place
│   ├── dto
│   ├── mapper
│   ├── dao
│   ├── service
│   └── controller
└── trip
    ├── dto
    ├── mapper
    ├── dao
    ├── service
    └── controller
```

## 각 계층의 역할

- DTO: Controller·Service·Mapper 사이에서 데이터를 전달한다.
- Mapper 인터페이스: 실행할 DB 작업의 메서드를 선언한다.
- Mapper XML: 실제 SQL을 작성한다.
- DAO: Mapper 호출을 감싸서 DB 접근 위치를 한 곳으로 모은다.
- Service: 트랜잭션과 업무 규칙을 처리한다.
- Controller: `/api` REST 요청을 Service에 연결한다.

## 추가된 API

| Controller | 기본 URL | 역할 |
| --- | --- | --- |
| `UserController` | `/api/users` | 회원 등록·조회·수정·탈퇴 |
| `PlaceController` | `/api/places` | 장소 등록·검색·조회·수정·삭제 |
| `TripController` | `/api/v1/trips` | 여행·날짜별 DAY 원자적 등록, 회원별 조회·수정·삭제 |
| `TripDayController` | `/api/v1/trips/{tripId}/days` | 여행 일자 조회·수정·삭제 및 개별 등록 |
| `ItineraryItemController` | `/api/v1/trip-days/{tripDayId}/items` | 세부 일정 등록·조회·수정·삭제 |
| `FavoriteController` | `/api/v1/favorites` | 찜 등록·목록·개수·장소별 상태 조회·해제 |

`ApiExceptionHandler`는 잘못된 ID나 날짜에 대해 HTTP 400과 오류 메시지를 JSON으로 반환한다.

## 실행 방법

화면만 확인할 때는 기존처럼 실행한다.

```powershell
.\gradlew.bat bootRun
```

PostgreSQL을 연결할 때는 Docker Compose로 `all_my_trips`를 시작하고 Flyway V1~V7을 적용한 뒤 local 프로필로 실행한다.

```powershell
docker compose up -d postgres
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

DB 계정은 `application-local.properties`와 `compose.yaml`에서 함께 변경해야 한다.

## 다음 구현 순서

1. 회원 비밀번호 암호화와 로그인 인증 연결
2. 여행 일자 자동 생성
3. 일정 항목 순서 변경 API 추가
4. 장소 외부 검색 결과와 places 테이블 연결
5. AI·예약·결제 도메인 추가
