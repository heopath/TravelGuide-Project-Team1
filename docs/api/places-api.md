# 장소 API

| 기능 | Method | URL | 인증 |
| --- | --- | --- | --- |
| 목록 조회 | GET | `/api/v1/places` | 불필요 |
| 생성 | POST | `/api/v1/places` | **필요** |

장소는 여러 사용자의 일정이 참조하는 공용 데이터이므로 생성만 로그인을 요구한다. 조회는 비로그인 탐색을 허용한다. 수정·삭제 API는 제거되었다. (PR #90)

오류 응답 형식과 401/403 구분은 [error-responses.md](error-responses.md)를 따른다.

## 목록 조회 요청

```http
GET /api/v1/places?page=0&size=20
Accept: application/json
```

- `page`: 0부터 시작하는 페이지 번호, 기본값 `0`
- `size`: 페이지 크기, 기본값 `20`, 최댓값 `100`
- `keyword`, `category`, `region`, `styleId`: 선택 검색 조건

## 성공 응답 예시

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": [
    {
      "placeId": 1,
      "name": "해운대",
      "category": "ATTRACTION",
      "region": "부산",
      "city": "해운대구"
    }
  ]
}
```

## 빈 목록 응답 예시

조회 결과가 없으면 오류 대신 빈 배열을 반환한다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": []
}
```

## 생성 요청

```http
POST /api/v1/places
Content-Type: application/json
Accept: application/json
X-CSRF-TOKEN: {GET /api/v1/csrf로 발급받은 토큰}

{
  "externalProvider": "KAKAO",
  "externalPlaceId": "1",
  "category": "ATTRACTION",
  "name": "성산일출봉",
  "countryCode": "KR",
  "region": "제주",
  "city": "서귀포시",
  "active": true
}
```

성공 시 `201 Created`를 반환한다.

### 실패 응답

| 상황 | 응답 | `code` |
| --- | --- | --- |
| 미인증 (CSRF 토큰 유무 무관) | 401 | `UNAUTHORIZED` |
| CSRF 토큰 누락·불일치 | 403 | — |

CSRF 검사가 인증 검사보다 앞에서 동작하므로, **토큰이 없으면 비로그인 상태여도 403**이 먼저 나온다. 401을 확인하려면 CSRF 토큰을 담아 보내야 한다.
