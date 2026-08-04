package org.example.all_my_trip_project.domain.ai.config;

import com.google.genai.ApiClient;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiHttpClientConfigTest {

    @Test
    void configuresGoogleGenAiSdkHttpTimeout() throws Exception {
        GoogleGenAiConnectionProperties properties = new GoogleGenAiConnectionProperties();
        properties.setApiKey("test-api-key");

        try (Client client = new GeminiHttpClientConfig().configuredGoogleGenAiClient(properties, 25_000)) {
            Field apiClientField = Client.class.getDeclaredField("apiClient");
            apiClientField.setAccessible(true);
            HttpOptions options = ((ApiClient) apiClientField.get(client)).httpOptions();

            assertThat(options.timeout()).contains(25_000);
        }
    }
}
