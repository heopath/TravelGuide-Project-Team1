# 사용자 여행 선호 API 명세

## 기본 규칙

- API 버전: `/api/v1`
- 인증 방식: 세션·쿠키
- 공통 성공 응답: `ApiResponse<T>`
- 공통 오류 응답: `ErrorResponse`
- 사용자 ID는 요청값으로 받지 않고 로그인 세션에서 조회한다.
- 이 API는 회원의 장기 여행 선호를 관리한다. 여행 한 건의 스타일은 `trip_travel_styles` 담당 API에서 관리한다.

## API 목록

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 내 여행 선호 조회 | GET | `/api/v1/members/me/preferences` | 필요 |
| 내 여행 선호 전체 교체 | PUT | `/api/v1/members/me/preferences` | 필요 |

## 내 여행 선호 조회

### 요청

```http
GET /api/v1/members/me/preferences
Cookie: JSESSIONID=...
```

### 성공 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": {
    "preferences": [
      {
        "travelStyleId": 1,
        "code": "SIGHTSEEING",
        "name": "관광",
        "preferenceScore": 90,
        "source": "EXPLICIT"
      }
    ]
  }
}
```

## 내 여행 선호 전체 교체

저장 요청에 포함되지 않은 기존 `EXPLICIT` 선호는 삭제한다. AI가 만든 `INFERRED` 선호는 유지한다. 기존 `INFERRED` 스타일을 사용자가 직접 선택하면 해당 항목은 `EXPLICIT`으로 변경한다.

### 요청

```http
PUT /api/v1/members/me/preferences
Content-Type: application/json
Cookie: JSESSIONID=...
```

```json
{
  "preferences": [
    {
      "travelStyleId": 1,
      "preferenceScore": 90
    },
    {
      "travelStyleId": 3,
      "preferenceScore": 80
    }
  ]
}
```

### 검증 규칙

- `preferences`는 필수이며 빈 배열을 보내면 사용자가 직접 등록한 선호를 모두 해제한다.
- 한 요청에서 여행 스타일 ID를 중복할 수 없다.
- 여행 스타일 ID는 1 이상이어야 한다.
- 비활성화되었거나 존재하지 않는 스타일은 저장할 수 없다.
- 선호 점수는 0~100 범위다.
- 최대 20개까지 저장할 수 있다.

### 주요 오류

| HTTP | 오류 코드 | 의미 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필수값 또는 점수 범위가 올바르지 않음 |
| 400 | `TRAVEL_STYLE_DUPLICATED` | 동일 스타일이 요청에 중복됨 |
| 401 | `UNAUTHORIZED` | 로그인하지 않았거나 회원을 찾을 수 없음 |
| 404 | `TRAVEL_STYLE_NOT_FOUND` | 사용할 수 없는 스타일이 포함됨 |

## 담당 경계

- 허민재: 회원 선호 조회·저장, 마이페이지 연동
- AI 담당: 이 API의 조회 결과를 추천 입력으로 사용하며 회원 선호 저장 코드를 직접 수정하지 않음
- 여행 계획 담당: 여행별 선택은 `trip_travel_styles`에 저장하며 `user_preferences`를 수정하지 않음
