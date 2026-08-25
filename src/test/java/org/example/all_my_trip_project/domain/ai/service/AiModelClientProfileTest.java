package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.global.apikey.ApiKeyCipher;
import org.example.all_my_trip_project.global.apikey.ApiKeyProvider;
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
    void aiProfileSelectsOpenAiClient() {
        try (AnnotationConfigApplicationContext context = createContext("ai")) {
            assertThat(context.getBean(AiModelClient.class)).isInstanceOf(OpenAiAiModelClient.class);
        }
    }

    private AnnotationConfigApplicationContext createContext(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.register(MockAiModelClient.class);
        context.register(OpenAiAiModelClient.class);
        /*
         * OpenAiAiModelClient는 키를 생성자에서 고정하지 않고 호출할 때마다 ApiKeyProvider에서
         * 꺼낸다. 어떤 구현이 선택되는지만 보는 테스트지만, 빈이 만들어져야 확인할 수 있다.
         * 저장소(ApiKeyStore)는 등록하지 않는다 — 없으면 설정값만 보도록 되어 있다.
         */
        context.register(ApiKeyCipher.class);
        context.register(ApiKeyProvider.class);
        context.refresh();
        return context;
    }
}
