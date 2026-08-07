package org.example.all_my_trip_project.domain.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class AiModelClientProfileTest {

    @Test
    void uiProfileSelectsMockClient() {
        try (AnnotationConfigApplicationContext context = createContext("ui")) {
            assertThat(context.getBean(AiModelClient.class)).isInstanceOf(MockAiModelClient.class);
        }
    }

    @Test
    void aiProfileSelectsCohereClient() {
        try (AnnotationConfigApplicationContext context = createContext("ai")) {
            assertThat(context.getBean(AiModelClient.class)).isInstanceOf(CohereAiModelClient.class);
        }
    }

    private AnnotationConfigApplicationContext createContext(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.register(MockAiModelClient.class);
        context.register(CohereAiModelClient.class);
        context.refresh();
        return context;
    }
}
