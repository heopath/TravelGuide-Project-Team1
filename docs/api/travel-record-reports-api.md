# 여행 기록 신고 API 명세

> 구현: `domain.social` (`TravelRecordReportController` → `TravelRecordReportService`). 담당: 남현호(작성 접수), 관리자 처리 화면은 허민재 담당 `admin/admin.html`([backend-service-role-plan.md](../backend-service-role-plan.md) 10절)과 연결될 예정.
> 상태: 백엔드 API 구현·컴파일 확인 완료, **프론트엔드 연동 전**. `trips/record.html`(신고 버튼)과 `admin/admin.html`(신고 처리 목록) 양쪽 모두 아직 이 API를 호출하지 않는다.

## 1. 기본 규칙

- API 버전: `/api/v1`
- 인증 방식: 세션·쿠키(`AuthenticatedUser` principal)
- 공통 성공 응답: `ApiResponse<T>`, 공통 오류 응답은 [error-responses.md](error-responses.md)를 따른다.
- 신고 대상 여행 기록의 존재·열람 가능 여부는 여행 기록 도메인의 공개 계약(`TravelRecordAccessGuard`)으로 확인한다. 신고자가 볼 수 없는(비공개이고 본인 소유가 아닌) 기록은 `RECORD_NOT_FOUND`로 응답해 존재 여부를 노출하지 않는다.
- 신고 처리(목록 조회·처리)는 **`AuthenticatedUser.role() == "ADMIN"`**인 사용자만 호출할 수 있다. 별도 `AdminService`/감사 로그 연동은 아직 없다 — 9절 참고.

## 2. API 목록

| 기능 | Method | URL | 인증 |
|---|---|---|---|
| 신고 접수 | POST | `/api/v1/travel-records/{travelRecordId}/reports` | 필요 |
| 신고 목록 조회 | GET | `/api/v1/travel-record-reports` | 필요(ADMIN) |
| 신고 처리 | PATCH | `/api/v1/travel-record-reports/{reportId}` | 필요(ADMIN) |

---

## 3. 신고 접수

동일 신고자가 같은 기록에 대해 `PENDING`/`REVIEWING` 상태의 신고를 이미 올렸다면 새 신고를 받지 않는다(처리 완료 후에는 다시 신고할 수 있다).

### 요청

```http
POST /api/v1/travel-records/{travelRecordId}/reports
Content-Type: application/json
Cookie: JSESSIONID=세션값
```

```json
{
  "reason": "INAPPROPRIATE",
  "detail": "여행 기록과 무관한 광고성 링크가 포함되어 있습니다."
}
```

### 요청 DTO — `ReportRecordRequest`

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---:|---|
| reason | String(enum) | O | `SPAM`, `ABUSE`, `INAPPROPRIATE`, `COPYRIGHT`, `PRIVACY`, `OTHER` 중 하나 |
| detail | String | X | 최대 1000자 |

### 성공 응답

- HTTP 상태: `201 Created`, `Location: /api/v1/travel-record-reports/{travelRecordReportId}`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "신고가 접수되었습니다.",
  "data": {
    "travelRecordReportId": 30,
    "travelRecordId": 101,
    "reporterUserId": 9,
    "reason": "INAPPROPRIATE",
    "detail": "여행 기록과 무관한 광고성 링크가 포함되어 있습니다.",
    "status": "PENDING",
    "processedBy": null,
    "processedAt": null,
    "resolutionNote": null,
    "createdAt": "2026-08-10T09:10:00Z"
  }
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | `reason` 누락 등 필드 검증 실패 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| RECORD_NOT_FOUND | 404 | 대상 기록이 없거나 신고자가 볼 수 없음(비공개+타인 소유) |
| REPORT_ALREADY_PENDING | 409 | 동일 신고자의 처리 대기 중인 신고가 이미 있음 |

---

## 4. 신고 목록 조회 (관리자)

### 요청

```http
GET /api/v1/travel-record-reports?status=PENDING
Cookie: JSESSIONID=세션값(ADMIN)
```

`status` 쿼리 파라미터는 선택이며 생략하면 전체 상태를 생성일 오름차순으로 반환한다.

### 성공 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 완료되었습니다.",
  "data": [
    {
      "travelRecordReportId": 30,
      "travelRecordId": 101,
      "reporterUserId": 9,
      "reason": "INAPPROPRIATE",
      "detail": "여행 기록과 무관한 광고성 링크가 포함되어 있습니다.",
      "status": "PENDING",
      "processedBy": null,
      "processedAt": null,
      "resolutionNote": null,
      "createdAt": "2026-08-10T09:10:00Z"
    }
  ]
}
```

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| FORBIDDEN | 403 | 로그인했지만 ADMIN 권한이 아님 |

---

## 5. 신고 처리 (관리자)

`RESOLVED` 또는 `REJECTED`로만 전환할 수 있다. 이미 처리된(`RESOLVED`/`REJECTED`) 신고를 다시 처리하려 하면 거부한다.

### 요청

```http
PATCH /api/v1/travel-record-reports/{reportId}
Content-Type: application/json
Cookie: JSESSIONID=세션값(ADMIN)
```

```json
{
  "status": "RESOLVED",
  "resolutionNote": "게시물에서 광고성 링크를 확인하여 기록을 비공개로 전환 조치했습니다."
}
```

### 요청 DTO — `ProcessReportRequest`

| 필드 | 타입 | 필수 | 검증 규칙 |
|---|---|---:|---|
| status | String(enum) | O | `RESOLVED`, `REJECTED` 중 하나만 허용(`PENDING`/`REVIEWING`으로는 전환 불가) |
| resolutionNote | String | X | 최대 1000자 |

### 성공 응답

`TravelRecordReportResponse`를 3절과 동일한 형태로 반환한다(`message`: "신고 처리 결과가 저장되었습니다.", `status`/`processedBy`/`processedAt`/`resolutionNote`가 채워진다).

### 발생 가능한 오류

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | `status` 누락 등 필드 검증 실패 |
| INVALID_REPORT_STATUS_TRANSITION | 400 | `status`가 `RESOLVED`/`REJECTED`가 아니거나, 이미 처리된 신고를 다시 처리하려는 경우 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| FORBIDDEN | 403 | 로그인했지만 ADMIN 권한이 아님 |
| REPORT_NOT_FOUND | 404 | 대상 신고가 없음 |

---

## 6. DTO 목록

| DTO | 용도 |
|---|---|
| ReportRecordRequest | 신고 접수 요청 |
| ProcessReportRequest | 신고 처리 요청 |
| TravelRecordReportResponse | 신고 응답(접수·목록 조회·처리 공통) |

## 7. 오류 코드 목록

| 오류 코드 | HTTP 상태 | 의미 |
|---|---:|---|
| VALIDATION_ERROR | 400 | Bean Validation 실패(공통) |
| INVALID_REPORT_STATUS_TRANSITION | 400 | 허용되지 않는 처리 상태 전이 |
| UNAUTHORIZED | 401 | 로그인이 필요함 |
| FORBIDDEN | 403 | ADMIN 권한이 아님 |
| RECORD_NOT_FOUND | 404 | 신고 대상 기록이 없거나 신고자가 볼 수 없음 |
| REPORT_NOT_FOUND | 404 | 신고 내역이 없음 |
| REPORT_ALREADY_PENDING | 409 | 처리 대기 중인 중복 신고 |

## 8. 미확정 항목 — 페이지 담당자·관리자 화면 담당자 논의 필요

1. **ADMIN 권한 검사가 컨트롤러의 임시 체크**: `AdminService`(감사 로그, 대시보드 집계)가 아직 없어 `TravelRecordReportController`가 `AuthenticatedUser.role()`을 직접 비교한다. `AdminService`가 만들어지면 [backend-service-role-plan.md](../backend-service-role-plan.md) 4.7절의 `recordAudit()` 호출 지점으로 이관이 필요하다.
2. **신고 목록에 페이지네이션 없음**: 신고가 많아지면 관리자 화면에서 `page`/`size`가 필요할 수 있다.
3. **신고 처리와 원본 기록 상태 연동 없음**: 신고를 `RESOLVED` 처리해도 여행 기록 자체의 `visibility`나 노출 여부는 자동으로 바뀌지 않는다. 기록을 비공개 전환·삭제하려면 관리자가 별도로 `TravelRecordService`를 호출해야 하는데, 관리자가 타인 소유 기록을 강제로 수정·삭제할 수 있는 API는 현재 없다(소유자 전용).

## 9. 완료 기준

- [x] 요청·응답 DTO 필드 확정
- [x] 오류 코드와 HTTP 상태 확정
- [x] 백엔드 컴파일·단위 배선 확인
- [ ] 페이지 담당자(남현호) 검수 — 8절 미확정 항목 확정
- [ ] 관리자 화면 담당자(허민재) 검수 — 8-1절 ADMIN 권한 검사 이관 방식 확정
- [ ] `trips/record.html`(신고 버튼) 연동
- [ ] `admin/admin.html`(신고 처리 목록) 연동
- [ ] 통합·단위 테스트 작성
