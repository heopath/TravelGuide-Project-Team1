package org.example.all_my_trip_project.domain.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.service.AiModelException;
import org.example.all_my_trip_project.global.apikey.ApiKeyProvider;
import org.example.all_my_trip_project.global.apikey.ManagedApiKey;
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
import java.util.function.Supplier;

/**
 * OpenAI Embeddings API 응답을 Spring AI {@link EmbeddingModel}로 연결한다.
 */
@Component
@Profile({"local-ai", "prod-ai-rag"})
public class OpenAiEmbeddingModel implements EmbeddingModel {

    private static final URI EMBED_URI = URI.create("https://api.openai.com/v1/embeddings");
    private static final int MAX_INPUTS_PER_REQUEST = 96;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    /** 관리자 화면에서 키를 교체하면 재시작 없이 반영되도록, 값이 아니라 조회 방법을 들고 있는다. */
    private final Supplier<String> apiKeySupplier;
    private final String model;
    private final int dimensions;
    private final Duration requestTimeout;

    @Autowired
    public OpenAiEmbeddingModel(
            ApiKeyProvider apiKeyProvider,
            @Value("${openai.embedding.model:text-embedding-3-small}") String model,
            @Value("${openai.embedding.dimensions:1536}") int dimensions,
            @Value("${openai.embedding.timeout-millis:25000}") long timeoutMillis
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build(),
                new ObjectMapper(), () -> apiKeyProvider.resolve(ManagedApiKey.OPENAI),
                model, dimensions, Duration.ofMillis(timeoutMillis));
    }

    /** 테스트가 키를 고정값으로 넘기던 방식을 그대로 유지한다. */
    OpenAiEmbeddingModel(HttpClient httpClient, ObjectMapper objectMapper, String apiKey,
                         String model, int dimensions, Duration requestTimeout) {
        this(httpClient, objectMapper, () -> apiKey, model, dimensions, requestTimeout);
    }

    OpenAiEmbeddingModel(HttpClient httpClient, ObjectMapper objectMapper, Supplier<String> apiKeySupplier,
                         String model, int dimensions, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKeySupplier = apiKeySupplier;
        this.model = model;
        this.dimensions = dimensions;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        Assert.notEmpty(texts, "Embedding texts must not be empty");
        Assert.isTrue(texts.size() <= MAX_INPUTS_PER_REQUEST,
                "OpenAI Embeddings supports at most " + MAX_INPUTS_PER_REQUEST + " texts per request");

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(EMBED_URI)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKeySupplier.get())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody(texts))))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiModelException("OpenAI embedding request failed. status=" + response.statusCode());
            }
            return toEmbeddingResponse(response.body(), texts.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiModelException("OpenAI embedding request was interrupted", exception);
        } catch (IOException exception) {
            throw new AiModelException("OpenAI embedding request failed", exception);
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
        body.put("input", texts);
        body.put("encoding_format", "float");
        body.put("dimensions", dimensions);
        return body;
    }

    private EmbeddingResponse toEmbeddingResponse(String body, int expectedCount) throws IOException {
        JsonNode vectors = objectMapper.readTree(body).path("data");
        if (!vectors.isArray() || vectors.size() != expectedCount) {
            throw new AiModelException("OpenAI embedding response has an invalid vector count");
        }

        List<Embedding> embeddings = new java.util.ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            JsonNode vector = vectors.get(index).path("embedding");
            if (!vector.isArray() || vector.size() != dimensions) {
                throw new AiModelException("OpenAI embedding response has an invalid vector dimension");
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
