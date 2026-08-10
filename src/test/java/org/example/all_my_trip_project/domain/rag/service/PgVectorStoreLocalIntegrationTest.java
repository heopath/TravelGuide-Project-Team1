package org.example.all_my_trip_project.domain.rag.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker PostgreSQL의 V10 스키마를 실제로 사용하는 통합 테스트다.
 * 외부 API 비용을 발생시키지 않도록 고정 임베딩 모델을 사용한다.
 */
@EnabledIfEnvironmentVariable(named = "RAG_LOCAL_INTEGRATION_TEST", matches = "true")
class PgVectorStoreLocalIntegrationTest {

    private static final String BUSAN_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SEOUL_ID = "22222222-2222-2222-2222-222222222222";
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            System.getenv().getOrDefault("RAG_TEST_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/all_my_trips"),
            System.getenv().getOrDefault("RAG_TEST_DATASOURCE_USERNAME", "allmytrips"),
            System.getenv().getOrDefault("RAG_TEST_DATASOURCE_PASSWORD", "local-secret")
    ));

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM public.vector_store WHERE id IN (?, ?)",
                java.util.UUID.fromString(BUSAN_ID), java.util.UUID.fromString(SEOUL_ID));
    }

    @Test
    void storesAndSearches1536DimensionVectorsInLocalPgvector() {
        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, new KeywordEmbeddingModel())
                .dimensions(1536)
                .initializeSchema(false)
                .build();
        store.add(List.of(
                Document.builder().id(BUSAN_ID).text("부산 광안리 해변 카페").metadata("source", "place:busan").build(),
                Document.builder().id(SEOUL_ID).text("서울 경복궁 한옥마을").metadata("source", "place:seoul").build()
        ));

        List<Document> results = store.similaritySearch(SearchRequest.builder()
                .query("부산 해변 추천")
                .topK(1)
                .build());

        assertThat(results).singleElement().satisfies(document -> {
            assertThat(document.getId()).isEqualTo(BUSAN_ID);
            assertThat(document.getMetadata()).containsEntry("source", "place:busan");
        });
        String embeddingType = jdbcTemplate.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname = 'vector_store' AND a.attname = 'embedding'
                """, String.class);
        assertThat(embeddingType).isEqualTo("vector(1536)");
    }

    private static final class KeywordEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new java.util.ArrayList<>();
            for (int index = 0; index < request.getInstructions().size(); index++) {
                String text = request.getInstructions().get(index);
                float[] vector = new float[1536];
                vector[containsBusan(text) ? 0 : 1] = 1.0f;
                embeddings.add(new Embedding(vector, index));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public int dimensions() {
            return 1536;
        }

        @Override
        public float[] embed(Document document) {
            return call(new EmbeddingRequest(List.of(document.getText()),
                    org.springframework.ai.embedding.EmbeddingOptions.builder().build()))
                    .getResults().getFirst().getOutput();
        }

        private boolean containsBusan(String text) {
            return text != null && text.contains("부산");
        }
    }
}
