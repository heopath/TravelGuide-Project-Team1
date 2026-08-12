package org.example.all_my_trip_project.domain.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.service.AiModelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cohere Developer API의 Embed v2 응답을 Spring AI {@link EmbeddingModel}로 연결한다.
 * RAG 문서를 저장할 때는 Cohere 권장값인 search_document 입력 유형을 사용한다.
 */
@Component
@Profile({"local-ai", "prod-ai-rag"})
public class CohereEmbeddingModel implements EmbeddingModel {

    private static final URI EMBED_URI = URI.create("https://api.cohere.com/v2/embed");
    private static final int MAX_INPUTS_PER_REQUEST = 96;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final Duration requestTimeout;

    @Autowired
    public CohereEmbeddingModel(
            @Value("${cohere.api-key}") String apiKey,
            @Value("${cohere.embedding.model:embed-v4.0}") String model,
            @Value("${cohere.embedding.dimensions:1536}") int dimensions,
            @Value("${cohere.embedding.timeout-millis:25000}") long timeoutMillis
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build(),
                new ObjectMapper(), apiKey, model, dimensions, Duration.ofMillis(timeoutMillis));
    }

    CohereEmbeddingModel(HttpClient httpClient, ObjectMapper objectMapper, String apiKey,
                         String model, int dimensions, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        Assert.notEmpty(texts, "Embedding texts must not be empty");
        Assert.isTrue(texts.size() <= MAX_INPUTS_PER_REQUEST,
                "Cohere Embed supports at most " + MAX_INPUTS_PER_REQUEST + " texts per request");

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(EMBED_URI)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("X-Client-Name", "all-my-trips")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody(texts))))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiModelException("Cohere embedding request failed. status=" + response.statusCode());
            }
            return toEmbeddingResponse(response.body(), texts.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiModelException("Cohere embedding request was interrupted", exception);
        } catch (IOException exception) {
            throw new AiModelException("Cohere embedding request failed", exception);
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(getEmbeddingContent(document));
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private Map<String, Object> requestBody(List<String> texts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input_type", "search_document");
        body.put("texts", texts);
        body.put("embedding_types", List.of("float"));
        body.put("output_dimension", dimensions);
        return body;
    }

    private EmbeddingResponse toEmbeddingResponse(String body, int expectedCount) throws IOException {
        JsonNode vectors = objectMapper.readTree(body).path("embeddings").path("float");
        if (!vectors.isArray() || vectors.size() != expectedCount) {
            throw new AiModelException("Cohere embedding response has an invalid vector count");
        }

        List<Embedding> embeddings = new java.util.ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            JsonNode vector = vectors.get(index);
            if (!vector.isArray() || vector.size() != dimensions) {
                throw new AiModelException("Cohere embedding response has an invalid vector dimension");
            }
            float[] values = new float[dimensions];
            for (int position = 0; position < dimensions; position++) {
                values[position] = (float) vector.get(position).asDouble();
            }
            embeddings.add(new Embedding(values, index));
        }
        return new EmbeddingResponse(embeddings);
    }
}
