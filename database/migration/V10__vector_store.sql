-- All My Trips Flyway migration: V10__vector_store
--
-- Cohere Embed v4.0의 출력 차원은 1536으로 고정한다.
-- vector(n)은 생성 후 차원을 변경할 수 없으므로, 모델 교체 시에는 별도
-- 벡터 테이블과 마이그레이션을 추가한다.
--
-- Required extensions (vector, hstore, uuid-ossp) are installed separately by
-- database/init/create_extensions.sql for local Docker and by the DB administrator
-- for production. Do not enable PgVectorStore automatic schema initialization in
-- production.

CREATE TABLE IF NOT EXISTS public.vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR(1536)
);

CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON public.vector_store USING HNSW (embedding vector_cosine_ops);
