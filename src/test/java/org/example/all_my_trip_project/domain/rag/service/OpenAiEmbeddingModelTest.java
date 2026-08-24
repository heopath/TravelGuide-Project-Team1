package org.example.all_my_trip_project.domain.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.service.AiModelException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiEmbeddingModelTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(
            httpClient, new ObjectMapper(), "test-key", "text-embedding-3-small", 3, Duration.ofSeconds(25));

    @Test
    void mapsOpenAiFloatEmbeddingsToSpringAiResponse() throws Exception {
        stubResponse(200, """
                {"data":[{"index":0,"embedding":[0.1,0.2,0.3]},{"index":1,"embedding":[0.4,0.5,0.6]}]}
                """);

        var response = model.call(new EmbeddingRequest(List.of("busan", "seoul"), EmbeddingOptions.builder().build()));

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().getFirst().getOutput()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(model.dimensions()).isEqualTo(3);
    }

    @Test
    void rejectsUnexpectedEmbeddingDimension() throws Exception {
        stubResponse(200, """
                {"data":[{"index":0,"embedding":[0.1,0.2]}]}
                """);

        assertThatThrownBy(() -> model.call(new EmbeddingRequest(List.of("busan"), EmbeddingOptions.builder().build())))
                .isInstanceOf(AiModelException.class)
                .hasMessage("OpenAI embedding response has an invalid vector dimension");
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }
}
