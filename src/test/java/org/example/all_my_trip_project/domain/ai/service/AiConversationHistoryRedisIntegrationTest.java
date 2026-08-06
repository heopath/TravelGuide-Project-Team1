package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 Redis 연결에서만 실행하는 동시성 통합 테스트입니다.
 * AI_REDIS_INTEGRATION_TEST=true 환경 변수로 명시적으로 활성화해야 합니다.
 */
@EnabledIfEnvironmentVariable(named = "AI_REDIS_INTEGRATION_TEST", matches = "true")
class AiConversationHistoryRedisIntegrationTest {

    private final Set<String> createdKeys = new HashSet<>();
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private AiConversationHistoryService historyService;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                environmentOrDefault("SPRING_DATA_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(environmentOrDefault("SPRING_DATA_REDIS_PORT", "16379"))
        );
        String password = System.getenv("SPRING_DATA_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            configuration.setPassword(password);
        }

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        verifyRedisConnection();

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> templateProvider = mock(ObjectProvider.class);
        when(templateProvider.getIfAvailable()).thenReturn(redisTemplate);
        @SuppressWarnings("unchecked")
        ObjectProvider<AiConversationPersistenceService> persistenceProvider = mock(ObjectProvider.class);
        historyService = new AiConversationHistoryService(templateProvider, persistenceProvider);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(createdKeys);
        connectionFactory.destroy();
    }

    @Test
    void keepsBothConcurrentTurnsAcrossTenRounds() throws Exception {
        for (int round = 0; round < 10; round++) {
            long userId = -(System.nanoTime() + round + 1);
            String key = "ai:guide:conversation:" + userId;
            createdKeys.add(key);

            historyService.append(userId, "seed-1", "answer-1");
            historyService.append(userId, "seed-2", "answer-2");
            historyService.append(userId, "seed-3", "answer-3");
            appendConcurrently(userId, "concurrent-a-" + round, "concurrent-b-" + round);

            List<AiConversationTurn> history = historyService.load(userId);
            assertThat(history).hasSize(3);
            assertThat(history).extracting(AiConversationTurn::question)
                    .contains("concurrent-a-" + round, "concurrent-b-" + round);
            assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 1800L);
        }
    }

    private void appendConcurrently(long userId, String firstQuestion, String secondQuestion) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                appendAfterStart(userId, firstQuestion, ready, start);
                return null;
            });
            var second = executor.submit(() -> {
                appendAfterStart(userId, secondQuestion, ready, start);
                return null;
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void appendAfterStart(long userId, String question, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start in time");
        }
        historyService.append(userId, question, "answer");
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void verifyRedisConnection() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Redis integration test requires a running tunnel and SPRING_DATA_REDIS_PASSWORD in this terminal.",
                    exception
            );
        }
    }
}
