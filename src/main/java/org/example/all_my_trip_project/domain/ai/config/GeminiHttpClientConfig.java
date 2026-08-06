package org.example.all_my_trip_project.domain.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

@Configuration
@Profile("ai")
public class GeminiHttpClientConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Client.class)
    public Client configuredGoogleGenAiClient(
            GoogleGenAiConnectionProperties properties,
            @Value("${ai.guide.gemini.http-timeout-millis:25000}") int timeoutMillis
    ) {
        Assert.hasText(properties.getApiKey(), "GEMINI_API_KEY must be configured for the ai profile");
        Assert.isTrue(timeoutMillis > 0, "Gemini HTTP timeout must be positive");

        return Client.builder()
                .apiKey(properties.getApiKey())
                .httpOptions(HttpOptions.builder().timeout(timeoutMillis).build())
                .build();
    }
}
