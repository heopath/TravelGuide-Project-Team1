# 인증 실패 응답 QA 테스트 케이스 (#95)

인증 실패는 401, CSRF 실패와 권한 부족은 403으로 구분한다. 규칙은 [error-responses.md](../api/error-responses.md) 참고.

## 사전 준비

API는 `local` 프로필로 띄운다. 기본 프로필이 `ui`이며 `ui`에서는 API 빈이 등록되지 않아 404가 난다. 404를 보고 인증 문제로 오판하기 쉽다.

```
-Dspring.profiles.active=local
```

## API 레벨

| # | 케이스 | 요청 | 기대 |
| --- | --- | --- | --- |
| A-1 | 미인증 조회 | `GET /api/v1/members/me` (비로그인) | 401, `code: UNAUTHORIZED` |
| A-2 | 미인증 생성 (CSRF 있음) | `POST /api/v1/trips` + CSRF 토큰, 비로그인 | 401 |
| A-3 | 미인증 생성 (CSRF 없음) | `POST /api/v1/places` 토큰 없이, 비로그인 | **403** (CSRF가 먼저 걸림) |
| A-4 | 로그인 + CSRF 없음 | `POST /api/v1/places` 토큰 없이, 로그인 | 403 |
| A-5 | 로그인 + CSRF 있음 | `POST /api/v1/places` 토큰 포함, 로그인 | 201 |
| A-6 | 비로그인 조회 허용 | `GET /api/v1/places` | 200 |

A-3이 401이 아니라 403인 것이 핵심이다. `CsrfFilter`가 `ExceptionTranslationFilter`보다 앞에서 자체 핸들러로 응답하므로 익명 사용자여도 403이 나온다.

### 응답 본문 확인

```json
{"success":false,"code":"UNAUTHORIZED","message":"로그인이 필요합니다.","data":null,"errors":[]}
```

## 화면 레벨

`여행 스타일` 화면(`/trips/new/style`)의 `여행 일정 만들기` 버튼 기준. 기본 정보와 스타일 3종을 먼저 입력해야 버튼이 동작한다.

| # | 케이스 | 조작 | 기대 |
| --- | --- | --- | --- |
| U-1 | 로그인 상태 | 버튼 클릭 | 확인창 없이 `/trips/{id}/schedule` 이동, 여행 생성됨 |
| U-2 | 로그아웃 상태 | 버튼 클릭 | `POST /api/v1/trips` 401 → 확인창 "로그인이 필요합니다. 로그인 페이지로 이동할까요?" |
| U-3 | 확인창 취소 | U-2에서 취소 | 화면 유지, 버튼 문구 `여행 일정 만들기`로 복구 |
| U-4 | 확인창 확인 | U-2에서 확인 | `/auth/login` 이동 |
| U-5 | 로그인 후 이동 | U-4에서 로그인 | `/home` (화면 안 401 경로는 복귀하지 않음, #96 절충안) |
| U-6 | 헤더 표시 | 로그인 상태 | 아바타·로그아웃 노출, `로그인` 버튼은 `display:none` |
| U-7 | 초안 보존 | U-4 → 로그인 → 스타일 화면 재진입 | 기본 정보와 스타일 선택이 유지됨 |

### 레이스 확인

U-1은 `dataset.authenticated` 설정 전에 눌러도 확인창이 뜨지 않아야 한다. `/api/v1/members/me` 응답을 기다리지 않고 `POST /api/v1/trips`를 먼저 보내 401로 판정하기 때문이다. 화면 로드 직후 즉시 클릭해 확인한다.

## 페이지 접근 제어 (#96)

| # | 케이스 | 조작 | 기대 |
| --- | --- | --- | --- |
| P-1 | 직접 진입 차단 | 비로그인으로 `/trips/new/basic` | `/auth/login?redirect=%2Ftrips%2Fnew%2Fbasic` 이동 |
| P-2 | 복귀 | P-1에서 로그인 | `/trips/new/basic` 복귀 |
| P-3 | 외부 주소 차단 | `/auth/login?redirect=/\evil.com`에서 로그인 | `/home` (외부 이동 없음) |
| P-4 | 공개 화면 | 비로그인으로 `/home`, `/guide` | 정상 표시 |
| P-5 | 정적 리소스 | 비로그인으로 CSS·JS 로드 | 200, 리다이렉트 없음 |
| P-6 | 관리자 화면 | `USER` 역할로 `/admin` | 403 |

P-3은 기존 문자열 검사(`//`만 차단)로는 통과하던 형태다. 브라우저가 역슬래시를 슬래시로 해석하므로 origin 비교로 막는다.

## 확인 이력

2026-08-06 운영 RDS 연결 상태에서 U-1~U-7, A-1을 브라우저로 확인해 모두 통과했다. A-2~A-6은 `PlaceSecurityTest`, P-1~P-6은 `PageAccessControlTest`가 자동 검증한다.
