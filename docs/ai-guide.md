# AI 여행 가이드 API (AI-02)

## 범위

화면은 `POST /api/v1/ai-guides/generate`를 호출합니다. 이번 단계에서는 Gemini와 DB를 연결하지 않고, `MockAiModelClient`가 DTO 형식의 응답을 반환합니다.

```text
AiGuideController → AiGuideService → AiModelClient → MockAiModelClient
```

실제 Gemini 연결은 AI-03에서 `GeminiAiModelClient` 구현으로 교체합니다. API 키는 `GEMINI_API_KEY` 환경 변수로만 관리하며 Git에 추가하지 않습니다.

## 요청과 응답

요청 본문은 질문만 받습니다.

```json
{ "question": "근처 저녁 맛집을 추천해줘" }
```

성공 시 `ApiResponse<AiGuideResponse>` 형식으로 `answer`, `days → items`, `externalLinks`, `sources`를 반환합니다. 샘플은 `docs/examples/ai-guide-response.json`에서 확인합니다.

## 화면 테스트

1. `http://localhost:8080/ai-guide`에 접속합니다.
2. 질문을 전송하면 로딩 뒤 서버 Mock 응답이 표시됩니다.
3. `API 테스트 상태`를 실패로 선택하면 `X-AI-Mock-Mode: server-error` 헤더로 서버 오류를 재현할 수 있습니다.
4. 성공으로 바꾼 뒤 다시 시도하면 정상 응답이 표시됩니다.

## 실행

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=ui"
```

UI 프로필은 DB와 Gemini 없이도 Mock API를 실행할 수 있습니다.
