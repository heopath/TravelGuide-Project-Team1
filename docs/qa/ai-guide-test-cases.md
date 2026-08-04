# AI-03 테스트 체크리스트

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| AI-UI-01 | `/ai-guide` 접속 | 질문 입력 화면, 부산 여행 컨텍스트, 외부 검색 카드가 표시된다. |
| AI-UI-02 | 추천 질문 칩 클릭 | 질문이 입력창에 들어가고 자동 전송되지 않는다. |
| AI-UI-03 | 정상 질문 전송 | 화면이 `POST /api/v1/ai-guides/generate`를 호출하고 사용자 메시지 → 채팅 내부 로딩 → DAY 1 일정·출처 순서로 표시된다. |
| AI-UI-04 | `<b>테스트</b>` 전송 | 굵은 글씨로 해석되지 않고 문자열 그대로 표시된다. |
| AI-UI-05 | `실패 응답` 선택 후 전송 | `X-AI-Mock-Mode: server-error`로 서버 오류가 발생하고 오류 문구와 다시 시도 버튼이 표시된다. |
| AI-UI-06 | 실패 후 `성공 응답` 전환, 다시 시도 | 마지막 질문으로 정상 응답이 표시된다. |
| AI-UI-07 | 항공권·숙소 링크 클릭 | 새 탭에서 Google Travel 검색 화면이 열린다. |
| AI-UI-08 | 헤더·전체 메뉴 버튼 클릭 | 현재 페이지가 의도치 않게 다시 로드되지 않고, 선택한 실제 메뉴만 이동한다. |
| AI-UI-09 | 모바일 폭 760px 이하 | 컨텍스트·채팅·예약 카드가 세로로 배치되고 가로 스크롤이 없다. |
| AI-API-01 | 빈 질문 API 요청 | 400, `success=false`, `VALIDATION_ERROR`가 반환된다. |
| AI-DOC-01 | Postman 컬렉션 확인 | 성공·검증 오류·Mock 서버 오류 요청을 실행할 수 있다. |

Postman 컬렉션은 AI API의 요청·응답과 Validation을 검증합니다. `ui` 프로필에서는 Mock 응답을, `ai,ai-local` 프로필에서는 Gemini 응답을 반환합니다.

## AI-03 Gemini 연동 확인

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| AI-GEMINI-01 | `GEMINI_API_KEY`를 환경 변수로 설정하고 `ai,ai-local` 프로필 실행 | 질문에 맞는 `answer`, `days → items` 응답이 화면에 표시되고 Mock 상태 선택 UI는 보이지 않는다. |
| AI-GEMINI-02 | Gemini가 호출·JSON 변환·SDK HTTP 25초 시간 초과에 실패 | `502`, `AI_GENERATION_FAILED`와 재시도 가능한 사용자 메시지가 반환되며 내부 예외 메시지와 API 키는 노출되지 않는다. |
| AI-GEMINI-03 | Docker PostgreSQL·Redis 실행 후 `local,ai,ai-integrated` 프로필 실행 | AI 가이드와 회원·마이페이지 등 DB 의존 화면이 함께 정상 동작한다. |
| AI-SEC-01 | 비로그인 사용자가 CSRF 토큰을 포함해 AI 생성 요청 | `403 Forbidden`으로 거절된다. |
| AI-SEC-02 | 로그인 사용자가 CSRF 토큰 없이 AI 생성 요청 | `403 Forbidden`으로 거절된다. |
| AI-SEC-03 | 로그인 사용자가 `GET /api/v1/csrf` 토큰을 `X-CSRF-TOKEN` 헤더에 포함해 AI 생성 요청 | `200 OK`와 AI 가이드 응답이 반환된다. |
| AI-GEMINI-04 | Gemini가 day, title, items, time, name, reason 규칙을 어긴 JSON 반환 | `502 AI_GENERATION_FAILED`를 반환하며 화면 렌더링 오류가 발생하지 않는다. |
| AI-HISTORY-01 | 로그인 사용자가 후속 질문 전송 | 최근 질문·응답 3세트가 Redis에서 조회되어 Gemini 프롬프트에 포함된다. |
| AI-HISTORY-02 | 4번째 대화 이력 저장 | 가장 오래된 대화가 제거되고 최근 3세트만 30분 TTL로 유지된다. |
| AI-HISTORY-03 | Redis 연결 실패 상태에서 AI 요청 | 질문 단독 Gemini 추천으로 대체되며 사용자에게 Redis 내부 오류를 노출하지 않는다. |

## AI-02-1 자동화 테스트 결과

| 실행 명령 | 결과 | 확인 내용 |
| --- | --- | --- |
| `./gradlew.bat test --tests org.example.all_my_trip_project.domain.ai.controller.AiGuideControllerTest` | 성공 | 정상 요청(200), 빈 질문·501자 초과 검증 오류(400), Mock 서버 오류(500) |
| `./gradlew.bat test` | 성공 | 현재 프로젝트 전체 테스트 통과 |
