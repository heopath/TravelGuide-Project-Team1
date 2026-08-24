# AI 모델 및 Spring AI 사용 기준

## 현재 운영 경로

| 기능 | 호출 방식 | 역할 |
| --- | --- | --- |
| 여행 AI 가이드 | `CohereAiModelClient`의 Cohere REST API | 여행·일정·실제 장소 기반 추천 |
| 고객센터 챗봇 | `SupportChatBotClient`의 Gemini REST API | 서비스 이용 문의 응대 및 상담원 전환 |
| RAG | Spring AI `EmbeddingModel`·`PgVectorStore` | Cohere 임베딩을 pgvector에 저장·검색 |

## Spring AI `ChatModel`

현재 프로젝트는 Spring AI `ChatModel`을 사용하지 않는다. 여행 가이드와 고객센터는 각 기능의 프롬프트·응답 형식·오류 정책에 맞는 REST 클라이언트를 직접 사용한다.

`GeminiAiModelClient`와 `gemini-legacy` 프로필은 제거했다. 새로운 기능에서 `ChatModel`을 도입하려면 기존 클라이언트를 억지로 교체하지 말고, 지원 공급자·응답 형식·프로필 구성·통합 테스트를 포함한 별도 설계 이슈로 먼저 합의한다.

## 의존성 원칙

Spring AI는 RAG용 벡터 저장소와 임베딩 인터페이스에만 유지한다. Google GenAI 채팅/임베딩 Starter는 더 이상 사용하지 않으며, Cohere 임베딩은 `CohereEmbeddingModel`이 REST API로 제공한다.
