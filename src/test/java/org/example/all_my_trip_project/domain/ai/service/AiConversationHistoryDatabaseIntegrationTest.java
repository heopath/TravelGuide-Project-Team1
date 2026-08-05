package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires a PostgreSQL/Redis tunnel and the same datasource/Redis environment variables used by IntelliJ.
 * The temporary user and trip are removed after every test.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.docker.compose.enabled=false"
})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "AI_HISTORY_DB_INTEGRATION_TEST", matches = "true")
class AiConversationHistoryDatabaseIntegrationTest {

    @Autowired
    private AiConversationPersistenceService persistenceService;
    @Autowired
    private AiConversationHistoryService historyService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long userId;
    private Long tripId;

    @AfterEach
    void tearDown() {
        if (userId != null && tripId != null) {
            persistenceService.delete(userId, tripId);
            jdbcTemplate.update("delete from trips where trip_id = ?", tripId);
        }
        if (userId != null) {
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    void storesConversationInDatabaseAndLoadsItAfterRedisHistoryIsRemoved() {
        createTemporaryUserAndTrip();

        persistenceService.append(userId, tripId, "first question", "first answer");

        Long sessionId = jdbcTemplate.queryForObject("""
                select ai_chat_session_id from ai_chat_sessions
                where user_id = ? and trip_id = ? and status = 'ACTIVE'
                """, Long.class, userId, tripId);
        Integer messageCount = jdbcTemplate.queryForObject("""
                select count(*) from ai_chat_messages where ai_chat_session_id = ?
                """, Integer.class, sessionId);
        assertThat(sessionId).isNotNull();
        assertThat(messageCount).isEqualTo(2);

        redisTemplate.delete(redisKey());

        assertThat(historyService.load(userId, tripId))
                .containsExactly(new AiConversationTurn("first question", "first answer"));
    }

    @Test
    void keepsBothConcurrentFirstTurnsInOneActiveSession() throws Exception {
        createTemporaryUserAndTrip();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> appendAfterStart(ready, start, "question-1", "answer-1"));
            Future<?> second = executor.submit(() -> appendAfterStart(ready, start, "question-2", "answer-2"));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        }

        Integer sessionCount = jdbcTemplate.queryForObject("""
                select count(*) from ai_chat_sessions
                where user_id = ? and trip_id = ? and status = 'ACTIVE'
                """, Integer.class, userId, tripId);
        Integer messageCount = jdbcTemplate.queryForObject("""
                select count(*) from ai_chat_messages message
                join ai_chat_sessions session on session.ai_chat_session_id = message.ai_chat_session_id
                where session.user_id = ? and session.trip_id = ? and session.status = 'ACTIVE'
                """, Integer.class, userId, tripId);

        assertThat(sessionCount).isEqualTo(1);
        assertThat(messageCount).isEqualTo(4);
    }

    private void appendAfterStart(CountDownLatch ready, CountDownLatch start, String question, String answer) {
        ready.countDown();
        try {
            start.await();
            persistenceService.append(userId, tripId, question, answer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted", exception);
        }
    }

    private void createTemporaryUserAndTrip() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, nickname, role, status)
                values (?, 'integration-test', ?, 'USER', 'ACTIVE')
                returning user_id
                """, Long.class, "ai-history-" + suffix + "@example.test", "ai-history-" + suffix);
        tripId = jdbcTemplate.queryForObject("""
                insert into trips (user_id, title, destination_name, start_date, end_date, companion_type, status, source)
                values (?, 'AI history integration test', 'Busan', ?, ?, 'SOLO', 'DRAFT', 'MANUAL')
                returning trip_id
                """, Long.class, userId, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2));
    }

    private String redisKey() {
        return "ai:guide:conversation:" + userId + ":trip:" + tripId;
    }
}
