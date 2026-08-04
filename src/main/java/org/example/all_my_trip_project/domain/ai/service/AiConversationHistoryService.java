package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AiConversationHistoryService {

    private static final int MAX_TURNS = 3;
    private static final Duration HISTORY_TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "ai:guide:conversation:";

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
            String serializedTurns = redisTemplate.opsForValue().get(key(userId));
            if (serializedTurns == null || serializedTurns.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(serializedTurns, new TypeReference<>() { });
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
            List<AiConversationTurn> turns = new ArrayList<>(load(userId));
            turns.add(new AiConversationTurn(question, answer));
            if (turns.size() > MAX_TURNS) {
                turns = new ArrayList<>(turns.subList(turns.size() - MAX_TURNS, turns.size()));
            }
            redisTemplate.opsForValue().set(key(userId), objectMapper.writeValueAsString(turns), HISTORY_TTL);
        } catch (Exception exception) {
            log.warn("AI 대화 이력을 저장하지 못했지만 추천 결과는 유지합니다. userId={}", userId, exception);
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
