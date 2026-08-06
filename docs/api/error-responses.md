# API 오류 응답 규칙

모든 `/api/**` 응답은 성공·실패 여부와 무관하게 동일한 본문 형식을 사용한다. (#95)

## 인증·권한 실패 구분

인증 실패와 CSRF 실패를 응답 코드로 구분한다. 기본 설정에서는 둘 다 403이라 프론트가 "로그인으로 보낼지" "토큰을 재발급할지" 판단할 수 없었다.

| 상황 | 응답 | 처리 주체 |
| --- | --- | --- |
| 미인증 | **401** | `ApiSecurityConfig`의 `AuthenticationEntryPoint` |
| CSRF 토큰 누락·불일치 | **403** | `CsrfFilter` (로그인 여부 무관) |
| 권한 부족 | **403** | `AccessDeniedHandler` |

`CsrfFilter`는 `ExceptionTranslationFilter`보다 앞에서 자체 핸들러로 응답한다. 따라서 **CSRF 실패는 익명 사용자여도 403**이며, 401은 인증 실패에만 쓰인다.

## 응답 본문

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다.",
  "data": null,
  "errors": []
}
```

| 필드 | 설명 |
| --- | --- |
| `success` | 실패 시 항상 `false` |
| `code` | 오류 식별자. 미인증은 `UNAUTHORIZED`, 권한 부족은 `ACCESS_DENIED` |
| `message` | 사용자 표시용 문구 |
| `data` | 실패 시 `null` |
| `errors` | 필드 검증 오류 목록. 없으면 빈 배열 |

## 프론트 처리 기준

- **401** → 로그인 유도
- **403** → CSRF 토큰 재발급 후 재시도

화면 로드 시점의 `document.documentElement.dataset.authenticated` 값으로 사전 판정하지 않는다. `auth-state.js`가 `/api/v1/members/me` 응답을 받은 뒤에야 설정되므로, 응답 도착 전에 버튼을 누르면 로그인한 사용자에게도 로그인 확인창이 뜬다. **요청을 보내고 401을 받아 처리하는 방식**을 쓴다.

## 인증이 필요한 엔드포인트

| Method | URL | 비고 |
| --- | --- | --- |
| POST | `/api/v1/trips` | 여행 생성 |
| POST | `/api/v1/places` | 장소는 여러 사용자가 참조하는 공용 데이터라 생성만 인증 요구. 조회(GET)는 공개 |
| POST | `/api/v1/ai-guides/generate` | AI 가이드 생성 |

## 페이지 요청

`/api/**`가 아닌 화면 요청은 401 JSON 대신 `/auth/login?redirect=...`으로 리다이렉트한다. (#96)
