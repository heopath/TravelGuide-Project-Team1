# AI 여행 가이드 API

## 현재 구현 범위

로그인 사용자의 여행·DAY·기존 일정·최근 대화·실제 장소 후보를 바탕으로 Cohere에 일정 추천을 요청합니다. 최근 대화는 Redis와 PostgreSQL에 보관하며, RAG와 카카오 장소 검색 결과를 실제 장소 후보로 활용합니다.

## 구성

AI 여행 가이드는 화면 DTO를 유지한 채 실행 프로필에 따라 모델 구현체를 선택합니다.

```text
AiGuideController → AiGuideService → AiModelClient
                                      ├─ MockAiModelClient   (ui / 기본 프로필)
                                      └─ CohereAiModelClient (ai 프로필)
```

- `ui` 또는 기본 프로필: DB와 외부 AI 없이 Mock 응답을 반환합니다.
- `ai` 프로필: Cohere Chat API(`api.cohere.com/v2/chat`)를 REST로 직접 호출하고, 모델 JSON을 `AiGuideResponse(days → items)`로 변환합니다.

Spring AI의 `ChatModel` 빈은 사용하지 않습니다. 여행 가이드는 Cohere REST API를 직접 호출하며,
RAG에서만 Spring AI의 임베딩 인터페이스와 `PgVectorStore`를 사용합니다. 자세한 역할 분리는
[`ai-model-routing.md`](ai-model-routing.md)를 참고합니다.

`ai`는 단독으로 쓰지 않고 실행 환경 프로필 위에 덧씌웁니다.

| 조합 | 용도 |
| --- | --- |
| `ui,ai` | DB 없이 AI 가이드 화면만 확인 |
| `local,ai` | 로컬 PostgreSQL·Redis와 함께 전체 기능 확인 |
| `prod,ai` | 운영 |

`ai` 프로필은 `spring.autoconfigure.exclude`를 설정하지 않습니다. 이 속성은 프로필 간에 병합되지 않고 통째로 덮어쓰기 때문에, 값을 지정하면 앞선 `ui`/`local`의 DB·Redis 제외 목록이 사라집니다.

## AI-05 여행·선호 컨텍스트

- AI 가이드 URL에 `/ai-guide?tripId={tripId}`를 전달하면 화면이 해당 값을 요청 본문의 `tripId`로 보냅니다.
- 서버는 기존 `TripService`, `TripDayService`, `ItineraryItemService`를 통해 로그인 사용자가 소유한 여행·DAY·일정 항목만 조회합니다. AI 가이드에서 여행, DAY, 일정 항목을 새로 생성하거나 수정하지 않습니다.
- 여행 목적지·기간·동행·예산·이동/음식/숙소 선호·DAY 제목/메모·기존 일정 항목과 사용자 여행 선호를 Cohere 요청 프롬프트에 전달합니다.
- `tripId`는 필수이며, 누락 또는 양의 정수가 아닌 값은 `400 VALIDATION_ERROR`로 거절합니다.
- 다른 사용자의 `tripId`를 전달하면 기존 여행 조회의 소유권 검증에 따라 요청이 거절됩니다.

여행 생성 화면은 `POST /api/v1/trips` 응답으로 받은 `tripId`를 일정 화면과 AI 가이드에 동일하게 전달해야 합니다. 임의의 `tripId`를 새로 만들거나 DB에 중복 저장하면 안 됩니다.

## API 계약

```http
POST /api/v1/ai-guides/generate
Content-Type: application/json

{ "question": "부산에서 하루 동안 갈 만한 곳을 추천해줘", "tripId": 12, "selectedDayNumber": 1 }
```

성공 응답은 `ApiResponse<AiGuideResponse>` 형식이며 `answer`, `days`, `externalLinks`, `sources`를 반환합니다. 일정 항목은 기본 `time`, `name`, `reason` 외에 실제 카카오/RAG 장소와 정확히 일치한 경우에만 `placeId`, `placeCategory`, `placeAddress`, `placeUrl`을 포함합니다. 화면은 이 메타데이터가 있는 항목에만 지도 보기와 일정 추가 기능을 표시합니다.

일정 화면에서 요청하면 현재 선택된 DAY 번호를 선택값 `selectedDayNumber`로 함께 보냅니다. 이 경우 AI는 해당 DAY의 기존 일정만 고려하고, 응답도 해당 DAY 하나만 반환합니다.

## 로컬 실행

Mock 화면 확인:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=ui"
```

Cohere 연결 확인(실제 키는 터미널/환경 변수에만 설정):

```powershell
$env:COHERE_API_KEY = "발급받은_키"
.\gradlew.bat bootRun --args="--spring.profiles.active=ui,ai"
```

`COHERE_API_KEY` 값은 `application*.properties`, Git, 문서, HTML, JavaScript에 절대 작성하거나 커밋하지 않습니다. AWS에서는 배포 환경 변수 또는 팀이 정한 Secret 저장소에서만 주입합니다.

전체 사이트와 Cohere를 함께 확인하려면 Docker Desktop에서 `docker compose up -d`로 PostgreSQL·Redis를 시작한 뒤 아래 프로필 조합으로 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,ai"
```

## 화면 테스트 UI

`API 테스트 상태`의 성공/실패 선택과 `X-AI-Mock-Mode` 실패 재현은 `ai.guide.mock.enabled=true`인 Mock 화면에서만 동작합니다. `ai` 프로필에서는 선택 UI가 표시되지 않고 실패 헤더도 무시됩니다.

AI 생성 API는 로그인과 CSRF 토큰이 모두 필요합니다. 화면은 요청 전 `GET /api/v1/csrf`에서 발급받은 토큰을 `X-CSRF-TOKEN` 헤더에 넣어 `POST /api/v1/ai-guides/generate`를 호출합니다.
