package org.example.all_my_trip_project.domain.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiModelClientProfileTest {

    @Test
    void uiProfileSelectsMockClient() {
        try (AnnotationConfigApplicationContext context = createContext("ui")) {
            assertThat(context.getBean(AiModelClient.class)).isInstanceOf(MockAiModelClient.class);
        }
    }

    @Test
    void aiProfileSelectsGeminiClient() {
        try (AnnotationConfigApplicationContext context = createContext("ai")) {
            assertThat(context.getBean(AiModelClient.class)).isInstanceOf(GeminiAiModelClient.class);
        }
    }

    private AnnotationConfigApplicationContext createContext(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.registerBean(ChatModel.class, () -> mock(ChatModel.class));
        context.register(MockAiModelClient.class, GeminiAiModelClient.class);
        context.refresh();
        return context;
    }
}
