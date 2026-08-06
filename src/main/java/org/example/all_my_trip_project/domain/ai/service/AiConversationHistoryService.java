package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class AiConversationHistoryService {

    private static final int MAX_TURNS = 3;
    private static final Duration HISTORY_TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "ai:guide:conversation:";
    private static final DefaultRedisScript<Long> APPEND_HISTORY_SCRIPT = new DefaultRedisScript<>("""
            redis.call('RPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], -%d, -1)
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """.formatted(MAX_TURNS), Long.class);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<AiConversationPersistenceService> persistenceServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiConversationHistoryService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<AiConversationPersistenceService> persistenceServiceProvider
    ) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.persistenceServiceProvider = persistenceServiceProvider;
    }

    public List<AiConversationTurn> load(Long userId) {
        return load(userId, null);
    }

    public List<AiConversationTurn> load(Long userId, Long tripId) {
        if (userId == null) {
            return List.of();
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                List<String> serializedTurns = redisTemplate.opsForList().range(key(userId, tripId), 0, -1);
                if (serializedTurns != null && !serializedTurns.isEmpty()) {
                    return serializedTurns.stream().map(this::deserialize).toList();
                }
            } catch (Exception exception) {
                log.warn("Failed to load recent AI conversation from Redis. userId={}", userId, exception);
            }
        }
        return loadFromDatabase(userId, tripId);
    }

    public void append(Long userId, String question, String answer) {
        append(userId, null, question, answer);
    }

    public void append(Long userId, Long tripId, String question, String answer) {
        if (userId == null) {
            return;
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                String serializedTurn = objectMapper.writeValueAsString(new AiConversationTurn(question, answer));
                redisTemplate.execute(
                        APPEND_HISTORY_SCRIPT,
                        List.of(key(userId, tripId)),
                        serializedTurn,
                        String.valueOf(HISTORY_TTL.toSeconds())
                );
            } catch (Exception exception) {
                log.warn("Failed to save recent AI conversation to Redis. userId={}", userId, exception);
            }
        }

        AiConversationPersistenceService persistenceService = persistenceServiceProvider.getIfAvailable();
        if (persistenceService != null) {
            try {
                persistenceService.append(userId, tripId, question, answer);
            } catch (Exception exception) {
                log.warn("Failed to save AI conversation to database. userId={}, tripId={}",
                        userId, tripId, exception);
            }
        }
    }

    public void delete(Long userId, Long tripId) {
        if (userId == null) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key(userId, tripId));
            } catch (Exception exception) {
                log.warn("Failed to delete recent AI conversation from Redis. userId={}", userId, exception);
            }
        }

        AiConversationPersistenceService persistenceService = persistenceServiceProvider.getIfAvailable();
        if (persistenceService != null) {
            try {
                persistenceService.delete(userId, tripId);
            } catch (Exception exception) {
                log.warn("Failed to delete AI conversation from database. userId={}", userId, exception);
            }
        }
    }

    private List<AiConversationTurn> loadFromDatabase(Long userId, Long tripId) {
        if (tripId == null) {
            return List.of();
        }
        AiConversationPersistenceService persistenceService = persistenceServiceProvider.getIfAvailable();
        if (persistenceService == null) {
            return List.of();
        }
        try {
            return persistenceService.loadRecentTurns(userId, tripId);
        } catch (Exception exception) {
            log.warn("Failed to load AI conversation from database. userId={}", userId, exception);
            return List.of();
        }
    }

    private AiConversationTurn deserialize(String serializedTurn) {
        try {
            return objectMapper.readValue(serializedTurn, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid AI conversation history", exception);
        }
    }

    private String key(Long userId, Long tripId) {
        return KEY_PREFIX + userId + (tripId == null ? "" : ":trip:" + tripId);
    }
}
