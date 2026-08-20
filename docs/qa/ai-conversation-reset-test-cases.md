# AI-11 새 대화 시작 QA 체크리스트

## 적용 범위

- 대상은 일정 화면(`/trips/{tripId}/schedule`)의 AI 일정 가이드이다.
- `새 대화 시작`은 현재 로그인 사용자와 현재 여행(`userId + tripId`)의 대화만 초기화한다.
- Redis 최근 이력은 즉시 삭제하고, DB의 ACTIVE 세션은 물리 삭제하지 않고 `ARCHIVED` 상태로 전환한다.
  따라서 기존 `ai_chat_messages` 행은 보존되며, 다음 질문은 새 ACTIVE 세션에 저장된다.
- 여행을 선택하지 않는 독립 `/ai-guide` 화면에는 현재 초기화 버튼을 제공하지 않는다. 해당 화면의
  `tripId` 없는 Redis 이력 정책은 별도 작업에서 정한다.

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

## 자동 검증

```powershell
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.ai.service.AiConversationHistoryServiceTest"
.\gradlew.bat test --tests "org.example.all_my_trip_project.domain.ai.service.AiConversationPersistenceServiceTest"
```

자동 테스트에서는 Redis 키 삭제, DB 세션 ARCHIVED 전환, 메시지 보존, 대상 외 사용자·여행 이력
미변경, Redis/DB 예외가 AI 기본 추천을 막지 않는지를 확인한다.
