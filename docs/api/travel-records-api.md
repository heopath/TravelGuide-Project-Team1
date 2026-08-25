# 여행 기록 API 명세

> **사진첩 기능**: 완료된 여행의 기록은 마이페이지 **여행 기록** 메뉴와 여행별 기록 화면에서 사진첩으로 확인할 수 있다.

> 구현: `domain.record` (`TravelRecordController` → `TravelRecordService`). 담당: 남현호(TRIP-00과 동일 담당 경계, [trip-service-structure.md](../trip-service-structure.md) 9절 참고).
> 상태: S3 파일 업로드와 사진첩 화면 연동 완료. 사진은 공개 버킷 URL이 아니라 공개 범위/소유권을 검사하는 API를 통해 제공한다.

## 1. 기본 규칙

- API 버전: `/api/v1`
- 인증 방식: 세션·쿠키(`AuthenticatedUser` principal)
- 공통 성공 응답: `ApiResponse<T>`, 공통 오류 응답은 [error-responses.md](error-responses.md)를 따른다.
- 사용자 ID는 요청값으로 받지 않고 로그인 세션에서 조회한다.
- 여행 기록은 `COMPLETED` 상태인 본인 여행에만, 여행당 최대 1건 작성할 수 있다([trip-service-structure.md](../trip-service-structure.md) 참고 — 완료 여행 확인은 `TripService.get()` 계약을 재사용한다).
- 공개 범위(`visibility`)는 `PRIVATE`, `PUBLIC` 두 값만 허용한다. `PUBLIC` 기록은 비로그인 사용자를 포함해 누구나 조회할 수 있고, `PRIVATE` 기록은 작성자 본인만 조회할 수 있다. 조회할 수 없는 기록은 존재 여부를 노출하지 않도록 항상 `404 RECORD_NOT_FOUND`로 응답한다.
- 이미지는 `PUT .../images` 호출마다 전체 목록을 교체하는 방식이며, 부분 추가·삭제 API는 없다.

## 2. API 목록

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 여행 기록 작성 | POST | `/api/v1/travel-records` | 필요 |
| 여행 기록 단건 조회 | GET | `/api/v1/travel-records/{travelRecordId}` | 선택(비공개 기록은 필요) |
| 내 여행 기록 목록 조회 | GET | `/api/v1/travel-records/me` | 필요 |
| 여행 기록 수정 | PUT | `/api/v1/travel-records/{travelRecordId}` | 필요 |
| 여행 기록 이미지 전체 교체 | PUT | `/api/v1/travel-records/{travelRecordId}/images` | 필요 |
| 여행 사진 S3 업로드 | POST | `/api/v1/travel-records/{travelRecordId}/images/upload` | 필요 |
| 여행 사진 조회 | GET | `/api/v1/travel-records/images/{travelRecordImageId}/content` | 선택(비공개 기록은 필요) |
| 여행 기록 삭제 | DELETE | `/api/v1/travel-records/{travelRecordId}` | 필요 |

---

## 3. 여행 기록 작성

### 요청

```http
POST /api/v1/travel-records
Content-Type: application/json
Cookie: JSESSIONID=세션값
```

```json
{
  "tripId": 12,
  "title": "제주 3박 4일 여행 기록",
  "content": "성산일출봉에서 본 일출이 최고였다.",
  "rating": 5,
  "visibility": "PUBLIC"
}
```

### 요청 DTO — `CreateTravelRecordRequest`

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---:|---|
| tripId | Long | O | 1 이상, 로그인 사용자 소유의 `COMPLETED` 여행이어야 함 |
| title | String | O | 공백 불가, 최대 200자 |
| content | String | O | 공백 불가 |
| rating | Short | X | 1~5 |
| visibility | String(enum) | O | `PRIVATE`, `PUBLIC` 중 하나 |

### 성공 응답

- HTTP 상태: `201 Created`, `Location: /api/v1/travel-records/{travelRecordId}`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "여행 기록이 작성되었습니다.",
  "data": {
    "travelRecordId": 101,
    "tripId": 12,
    "userId": 7,
    "title": "제주 3박 4일 여행 기록",
    "content": "성산일출봉에서 본 일출이 최고였다.",
    "rating": 5,
    "visibility": "PUBLIC",
    "images": [],
    "createdAt": "2026-08-10T09:00:00Z",
    "updatedAt": "2026-08-10T09:00:00Z"
  }
}
```

이미지는 별도 API로 등록하므로 생성 직후 응답의 `images`는 항상 빈 배열이다.

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | 필수값·길이·범위 검증 실패 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| TRIP_NOT_FOUND | 404 | 여행이 없거나 본인 소유가 아님(존재 여부를 노출하지 않기 위해 소유권 실패도 404) |
| TRIP_NOT_COMPLETED | 400 | `COMPLETED` 상태가 아닌 여행에 기록을 작성하려는 경우 |
| RECORD_ALREADY_EXISTS | 409 | 해당 여행에 이미 기록이 존재함(여행당 1건 제한) |

---

## 4. 여행 기록 단건 조회

### 요청

```http
GET /api/v1/travel-records/{travelRecordId}
```

비로그인 요청도 허용한다. `PUBLIC` 기록은 원문 그대로 반환하고, `PRIVATE` 기록은 작성자 본인일 때만 반환한다.

### 성공 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": {
    "travelRecordId": 101,
    "tripId": 12,
    "userId": 7,
    "title": "제주 3박 4일 여행 기록",
    "content": "성산일출봉에서 본 일출이 최고였다.",
    "rating": 5,
    "visibility": "PUBLIC",
    "images": [
      {
        "travelRecordImageId": 501,
        "imageUrl": "https://cdn.example.com/records/101/1.jpg",
        "altText": "성산일출봉 일출",
        "sortOrder": 1,
        "cover": true
      }
    ],
    "createdAt": "2026-08-10T09:00:00Z",
    "updatedAt": "2026-08-10T09:00:00Z"
  }
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| RECORD_NOT_FOUND | 404 | 기록이 없거나, 소프트 삭제됐거나, `PRIVATE`인데 작성자가 아님 — 세 경우를 구분하지 않고 동일하게 404 |

---

## 5. 내 여행 기록 목록 조회

### 요청

```http
GET /api/v1/travel-records/me
Cookie: JSESSIONID=세션값
```

작성일 최신순으로 전체 목록을 반환한다. **페이지네이션이 없다** — 장소·즐겨찾기 API(`page`/`size`)와 다른 점이니 목록이 커질 가능성이 있으면 프론트 연동 전에 확인이 필요하다(9절 참고).

### 성공 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": [
    {
      "travelRecordId": 101,
      "tripId": 12,
      "userId": 7,
      "title": "제주 3박 4일 여행 기록",
      "content": "성산일출봉에서 본 일출이 최고였다.",
      "rating": 5,
      "visibility": "PUBLIC",
      "images": [],
      "createdAt": "2026-08-10T09:00:00Z",
      "updatedAt": "2026-08-10T09:00:00Z"
    }
  ]
}
```

기록이 없으면 오류 대신 빈 배열을 반환한다.

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| UNAUTHORIZED | 401 | 로그인이 필요함 |

---

## 6. 여행 기록 수정

`title`, `content`, `rating`, `visibility` 전체를 다시 받는 전체 수정이다. 부분 수정(PATCH)은 지원하지 않는다. `tripId`는 수정 대상이 아니다.

### 요청

```http
PUT /api/v1/travel-records/{travelRecordId}
Content-Type: application/json
Cookie: JSESSIONID=세션값
```

```json
{
  "title": "제주 3박 4일 여행 기록 (수정)",
  "content": "우도 맛집도 다녀왔다.",
  "rating": 4,
  "visibility": "PRIVATE"
}
```

### 요청 DTO — `UpdateTravelRecordRequest`

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---:|---|
| title | String | O | 공백 불가, 최대 200자 |
| content | String | O | 공백 불가 |
| rating | Short | X | 1~5 |
| visibility | String(enum) | O | `PRIVATE`, `PUBLIC` 중 하나 |

### 성공 응답

`TravelRecordResponse`를 3절과 동일한 형태로 반환한다(`message`: "여행 기록이 수정되었습니다.").

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | 필수값·길이·범위 검증 실패 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| RECORD_NOT_FOUND | 404 | 기록이 없거나 본인 소유가 아님 |

---

## 7. 여행 기록 이미지 전체 교체

기존 이미지를 모두 삭제하고 요청 목록으로 다시 채운다. 목록 순서가 곧 `sortOrder`이며, `cover: true`는 최대 1개만 허용한다.

### 요청

```http
PUT /api/v1/travel-records/{travelRecordId}/images
Content-Type: application/json
Cookie: JSESSIONID=세션값
```

```json
{
  "images": [
    { "imageUrl": "https://cdn.example.com/records/101/1.jpg", "altText": "성산일출봉 일출", "cover": true },
    { "imageUrl": "https://cdn.example.com/records/101/2.jpg", "altText": "우도 전경", "cover": false }
  ]
}
```

### 요청 DTO — `ReplaceRecordImagesRequest`

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---:|---|
| images | List\<ImageItem\> | O | 최대 20개, `cover=true`는 0~1개만 허용 |
| images[].imageUrl | String | O | 공백 불가, 최대 1000자 |
| images[].altText | String | X | 최대 255자 |
| images[].cover | boolean | O | 기본값 없음, 명시적으로 보내야 함 |

`imageUrl`에는 업로드 API가 반환한 내부 S3 참조 또는 기존 이미지 조회 URL을 보낼 수 있다. 응답의 `imageUrl`은 항상 `/api/v1/travel-records/images/{id}/content` 형태이며, 버킷 객체 키를 직접 공개하지 않는다.

### 성공 응답

`TravelRecordResponse`를 3절과 동일한 형태로 반환한다(`message`: "여행 기록 이미지가 수정되었습니다.").

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | 목록 크기·URL 길이 등 필드 검증 실패 |
| INVALID_RECORD_REQUEST | 400 | `cover=true`가 2개 이상 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| RECORD_NOT_FOUND | 404 | 기록이 없거나 본인 소유가 아님 |

---

## 8. 여행 사진 S3 업로드·조회

```http
POST /api/v1/travel-records/{travelRecordId}/images/upload
Content-Type: multipart/form-data

file=@photo.jpg
```

- 허용 형식: JPEG, PNG, WEBP, GIF
- 최대 크기: 10MB
- 대상 기록의 작성자만 업로드할 수 있다.
- S3 설정은 `TRAVEL_RECORD_S3_ENABLED=true`, `AWS_S3_BUCKET`, `AWS_REGION`으로 켠다. 운영은 IAM Role, 로컬은 AWS SDK 기본 자격 증명 환경 변수로 인증한다.
- 사진 조회는 PUBLIC 기록이면 누구나, PRIVATE 기록이면 작성자만 가능하다.

## 9. 여행 기록 삭제

소프트 삭제(`deleted_at` 기록)이며 복구 API는 없다.

### 요청

```http
DELETE /api/v1/travel-records/{travelRecordId}
Cookie: JSESSIONID=세션값
```

### 성공 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "여행 기록이 삭제되었습니다.",
  "data": null
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| RECORD_NOT_FOUND | 404 | 기록이 없거나 본인 소유가 아님 |

---

## 10. DTO 목록

| DTO | 용도 |
|---|---|
| CreateTravelRecordRequest | 여행 기록 작성 요청 |
| UpdateTravelRecordRequest | 여행 기록 수정 요청(전체 교체) |
| ReplaceRecordImagesRequest / ImageItem | 이미지 전체 교체 요청 |
| TravelRecordResponse | 여행 기록 응답(생성·조회·수정·이미지 교체 공통) |
| TravelRecordImageResponse | `TravelRecordResponse.images`의 원소 |

## 11. 오류 코드 목록

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | Bean Validation 실패(공통) |
| INVALID_RECORD_REQUEST | 400 | 이미지 목록 등 DTO 조합 검증 실패 |
| TRIP_NOT_COMPLETED | 400 | 완료되지 않은 여행에 기록 작성 시도 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| TRIP_NOT_FOUND | 404 | 대상 여행이 없거나 본인 소유가 아님 |
| RECORD_NOT_FOUND | 404 | 대상 기록이 없거나 볼 수 없음 |
| RECORD_ALREADY_EXISTS | 409 | 여행당 1건 제한 위반 |

## 12. 구현 범위

아래 항목은 이 문서를 쓰면서 발견했지만 기존 기획 문서([backend-service-role-plan.md](../backend-service-role-plan.md), [trip-service-structure.md](../trip-service-structure.md))에 명시적인 답이 없어 확정하지 않았다. `trips/record.js` 연동 전에 GitHub 이슈에서 논의해 확정한다.

1. 사진 업로드는 완료 여행에서 만든 기록에만 가능하다. 업로드 버튼을 처음 누르면 비어 있는 기록도 기본 제목/메모/전체 공개로 생성된다.
2. 마이페이지 사진첩은 내 기록 목록을 최신 작성일 순으로 보여 준다. 페이지네이션은 기록 수가 증가하면 별도 이슈로 추가한다.
3. GIF 내보내기는 브라우저에서 생성하며, 업로드 사진 원본은 S3에 계속 보관된다.

## 13. 완료 기준

- [x] 요청·응답 DTO 필드 확정
- [x] 오류 코드와 HTTP 상태 확정
- [x] 백엔드 컴파일·단위 배선 확인
- [x] 완료 여행 전용 사진첩·마이페이지 진입점 연결
- [x] S3 업로드와 PUBLIC/PRIVATE 이미지 접근 제어
- [x] PNG/GIF 사진첩 내보내기
- [x] `trips/record.html` / `static/js/pages/trips/record.js` 실제 연동
- [ ] S3 실제 버킷 통합 테스트 — AWS 자격 증명·테스트 버킷이 필요한 별도 환경에서 수행
