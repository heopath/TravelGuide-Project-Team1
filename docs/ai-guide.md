# AI 여행 가이드 API (AI-03)

## 현재 구현 범위

이번 AI-03은 **사용자 질문 기반 Gemini 일정 생성** 범위입니다. Gemini에 전달하는 정보는 질문 문자열뿐이며, 현재 여행, 사용자 선호, 기존 일정, 장소 DB는 아직 프롬프트에 포함하지 않습니다. 해당 데이터 연동과 RAG는 후속 작업으로 분리합니다.

## 구성

AI 여행 가이드는 현재 화면 DTO를 유지한 채 실행 프로필에 따라 모델 구현체를 선택합니다.

```text
AiGuideController → AiGuideService → AiModelClient
                                      ├─ MockAiModelClient   (ui / 기본 프로필)
                                      └─ GeminiAiModelClient (ai 프로필)
```

- `ui` 또는 기본 프로필: DB와 외부 AI 없이 Mock 응답을 반환합니다.
- `ai` 프로필: Spring AI의 Google GenAI ChatModel로 Gemini를 호출하고, 모델 JSON을 `AiGuideResponse(days → items)`로 변환합니다.
- `ai-local` 프로필: 로컬 Gemini 확인 시 DB·Redis·임베딩 자동 설정만 끄고, 다른 DB 의존 기능은 지연 초기화하여 AI 가이드 화면만 단독 확인할 수 있게 합니다.
- `ai-integrated` 프로필: `local`의 PostgreSQL·Redis 설정을 유지하면서 Gemini ChatModel을 다시 켜는 전체 화면 통합 확인용 프로필입니다.
- `prod,ai` 프로필: 운영 DB 설정은 `prod`에서 유지하고 AI 관련 설정만 `ai`에서 추가합니다.

## API 계약

```http
POST /api/v1/ai-guides/generate
Content-Type: application/json

{ "question": "부산에서 하루 동안 갈 만한 곳을 추천해줘" }
```

성공 응답은 `ApiResponse<AiGuideResponse>` 형식이며 `answer`, `days`, `externalLinks`, `sources`를 반환합니다. Gemini에는 질문 기반 일정 JSON만 반환하도록 요청하고, 파싱 실패·모델 호출 실패·SDK HTTP 25초 시간 초과는 내부 상세를 노출하지 않는 `502 AI_GENERATION_FAILED` 응답으로 처리합니다. Google GenAI SDK의 `HttpOptions.timeout`을 사용하므로 시간 초과 시 HTTP 요청이 SDK 수준에서 종료되며, 별도 virtual thread 작업을 남기지 않습니다.

## 로컬 실행

Mock 화면 확인:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=ui"
```

Gemini 연결 확인(실제 키는 터미널/환경 변수에만 설정):

```powershell
$env:GEMINI_API_KEY = "발급받은_키"
.\gradlew.bat bootRun --args="--spring.profiles.active=ai,ai-local"
```

`GEMINI_API_KEY` 값은 `application*.properties`, Git, 문서, HTML, JavaScript에 절대 작성하거나 커밋하지 않습니다. AWS에서는 배포 환경 변수 또는 팀이 정한 Secret 저장소에서만 주입합니다.

전체 사이트와 Gemini를 함께 확인하려면 Docker Desktop에서 `docker compose up -d`로 PostgreSQL·Redis를 시작한 뒤 아래 프로필 조합으로 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,ai,ai-integrated"
```

## 화면 테스트 UI

`API 테스트 상태`의 성공/실패 선택과 `X-AI-Mock-Mode` 실패 재현은 `ai.guide.mock.enabled=true`인 Mock 화면에서만 동작합니다. `ai` 프로필에서는 선택 UI가 표시되지 않고 실패 헤더도 무시됩니다. 로컬 실제 Gemini 확인은 `ui,ai`가 아니라 `ai,ai-local` 프로필 조합을 사용합니다.

AI 생성 API는 로그인과 CSRF 토큰이 모두 필요합니다. 화면은 요청 전 `GET /api/v1/csrf`에서 발급받은 토큰을 `X-CSRF-TOKEN` 헤더에 넣어 `POST /api/v1/ai-guides/generate`를 호출합니다.
