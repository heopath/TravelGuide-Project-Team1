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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiConversationHistoryService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public List<AiConversationTurn> load(Long userId) {
        if (userId == null) {
            return List.of();
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return List.of();
        }

        try {
            List<String> serializedTurns = redisTemplate.opsForList().range(key(userId), 0, -1);
            if (serializedTurns == null || serializedTurns.isEmpty()) {
                return List.of();
            }
            return serializedTurns.stream().map(this::deserialize).toList();
        } catch (Exception exception) {
            log.warn("AI 대화 이력을 불러오지 못해 질문 단독 추천으로 처리합니다. userId={}", userId, exception);
            return List.of();
        }
    }

    public void append(Long userId, String question, String answer) {
        if (userId == null) {
            return;
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }

        try {
            String serializedTurn = objectMapper.writeValueAsString(new AiConversationTurn(question, answer));
            redisTemplate.execute(
                    APPEND_HISTORY_SCRIPT,
                    List.of(key(userId)),
                    serializedTurn,
                    String.valueOf(HISTORY_TTL.toSeconds())
            );
        } catch (Exception exception) {
            log.warn("AI 대화 이력을 저장하지 못했지만 추천 결과는 유지합니다. userId={}", userId, exception);
        }
    }

    private AiConversationTurn deserialize(String serializedTurn) {
        try {
            return objectMapper.readValue(serializedTurn, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid AI conversation history", exception);
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
