# AI-11 새 대화 시작 QA 체크리스트

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| AI-RESET-01 | 일정 화면의 AI 일정 가이드에서 질문 후 `새 대화 시작` 클릭 | 현재 사용자·현재 여행의 Redis 최근 대화가 삭제되고, 화면은 새 대화 안내 메시지로 초기화된다. |
| AI-RESET-02 | 초기화 후 다시 질문 | 이전 질문·응답이 프롬프트에 포함되지 않고 새 ACTIVE 세션에 저장된다. |
| AI-RESET-03 | DB 세션 확인 | 기존 ACTIVE 세션은 `ARCHIVED`가 되며 메시지 행은 삭제되지 않는다. |
| AI-RESET-04 | 다른 여행 또는 다른 사용자 대화 확인 | 대상 `userId + tripId` 이외의 Redis/DB 대화 이력은 유지된다. |
| AI-RESET-05 | Redis 또는 DB 초기화 처리 실패 | 오류는 로그에 남고, 이후 기본 AI 추천 기능은 계속 사용할 수 있다. |
| AI-RESET-06 | 비로그인 요청 | `DELETE /api/v1/ai-guides/conversation?tripId={tripId}`는 401을 반환한다. |
| AI-RESET-07 | 로그인 후 CSRF 토큰 없이 요청 | 403을 반환한다. |
| AI-RESET-08 | 로그인 및 유효 CSRF 토큰으로 요청 | 200과 공통 `ApiResponse` 형식을 반환한다. |

## Postman

1. 로그인 후 `GET /api/v1/csrf`로 CSRF 토큰을 받는다.
2. `DELETE /api/v1/ai-guides/conversation?tripId={{tripId}}` 요청에 `{{csrfHeaderName}}: {{csrfToken}}` 헤더를 넣는다.
3. 응답이 200인지 확인한 뒤 AI 생성 요청을 다시 보내 이전 대화 맥락이 포함되지 않는지 확인한다.
