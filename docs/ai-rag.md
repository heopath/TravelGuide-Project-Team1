# OpenAI RAG 실행 및 운영 규칙

## 모델과 차원

- 채팅 공급자: OpenAI Responses API
- 채팅 모델: `gpt-5.6-terra`
- 임베딩 공급자: OpenAI Embeddings API
- 임베딩 모델: `text-embedding-3-small`
- 임베딩 차원: `1536`
- 거리 방식: cosine
- 인덱스: pgvector HNSW

`vector(1536)`은 고정 타입이다. 다른 차원의 모델로 바꾸려면 기존 테이블을 변경하지 말고 별도 테이블과 마이그레이션을 추가한다.

## 로컬 검증

Docker PostgreSQL·Redis를 실행한 후 IntelliJ 실행 프로필을 다음처럼 설정한다.

```text
local,ai,local-ai
```

`OPENAI_API_KEY`는 IntelliJ 환경 변수로만 설정한다. RAG 장소 전체 색인은 OpenAI Embeddings API 호출을 사용하므로, 필요한 경우에만 아래 환경 변수를 추가한다.

```text
AI_RAG_REINDEX_ON_STARTUP=true
```

로컬 데이터베이스는 `all_my_trips`이며, `application-local-ai.properties`에서만
`spring.ai.vectorstore.pgvector.initialize-schema=true`를 허용한다.

## 운영 적용

운영 프로필은 다음 조합을 사용한다.

```text
prod,ai,prod-ai-rag
```

운영 데이터베이스 `all_my_trip`에는 Flyway `V10__vector_store.sql`이 먼저 적용되어야 한다.
`application-prod-ai-rag.properties`는 `initialize-schema=false`를 유지한다. 운영 기동 중 테이블·인덱스를 자동 생성하거나 전체 재색인하지 않는다.

기존 Cohere 벡터와 OpenAI 벡터는 같은 `1536` 차원을 사용해도 서로 다른 의미 공간이다. 공급자 전환 직후에는 운영자가 `AI_RAG_REINDEX_ON_STARTUP=true`로 한 번만 실행하여 모든 장소를 OpenAI 임베딩으로 재색인해야 한다.

## 장애 처리

장소 RAG 검색이 실패하거나 결과가 없으면 빈 RAG 컨텍스트로 진행한다. AI 질문, 현재 여행 컨텍스트, 대화 이력 기반의 기본 추천은 계속 제공한다.
