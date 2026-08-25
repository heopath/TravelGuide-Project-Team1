# AI 모델 및 Spring AI 사용 기준

## 현재 운영 경로

| 기능 | 호출 방식 | 역할 |
| --- | --- | --- |
| 여행 AI 가이드 | `OpenAiAiModelClient`의 OpenAI Responses API (`/v1/responses`) | 여행·일정·실제 장소 기반 추천 |
| 고객센터 챗봇 | `SupportChatBotClient`의 Gemini REST API | 서비스 이용 문의 응대 및 상담원 전환 |
| RAG | Spring AI `EmbeddingModel`·`PgVectorStore` | OpenAI 임베딩을 pgvector에 저장·검색 |

모델과 시간 제한은 설정으로 바꾼다. 여행 가이드는 `openai.chat.model`, 임베딩은 `openai.embedding.model`·`openai.embedding.dimensions`이며 기본값은 `application-ai.properties`와 `application-*-ai-rag.properties`에 있다. API 키는 `OPENAI_API_KEY`·`GEMINI_API_KEY` 환경변수로 넣는다.

두 기능이 서로 다른 공급자를 쓰는 이유는 성격이 달라서다. 여행 가이드는 일정·장소를 다루는 긴 추론과 정해진 형식의 응답이 필요하고, 고객센터 챗봇은 짧은 문의 응대와 상담원 전환 판단이 필요하다. 프롬프트·응답 형식·장애 처리 정책이 함께 가지 않으므로 한 클라이언트로 묶지 않는다.

## Spring AI `ChatModel`

현재 프로젝트는 Spring AI `ChatModel`을 사용하지 않는다. 여행 가이드와 고객센터는 각 기능의 프롬프트·응답 형식·오류 정책에 맞는 REST 클라이언트를 직접 사용한다.

`GeminiAiModelClient`와 `gemini-legacy` 프로필은 제거했다. 새로운 기능에서 `ChatModel`을 도입하려면 기존 클라이언트를 억지로 교체하지 말고, 지원 공급자·응답 형식·프로필 구성·통합 테스트를 포함한 별도 설계 이슈로 먼저 합의한다.

## 의존성 원칙

Spring AI는 RAG용 벡터 저장소와 임베딩 인터페이스에만 유지한다. Google GenAI 채팅/임베딩 Starter는 사용하지 않으며, OpenAI 임베딩은 `OpenAiEmbeddingModel`이 REST API로 제공한다.

임베딩 모델을 바꾸면 벡터 차원이 달라진다. pgvector 열의 차원과 이미 저장된 벡터가 함께 어긋나므로, 모델 교체는 재색인 계획을 세운 뒤에 진행한다.
