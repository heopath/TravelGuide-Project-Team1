package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConversationHistoryServiceTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiConversationHistoryService historyService;

    @BeforeEach
    void setUp() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        historyService = new AiConversationHistoryService(redisTemplateProvider);
    }

    @Test
    void storesOnlyThreeRecentTurnsPerUserWithThirtyMinuteTtl() throws Exception {
        for (int index = 1; index <= 4; index++) {
            when(valueOperations.get("ai:guide:conversation:1"))
                    .thenReturn(index == 1 ? null : objectMapper.writeValueAsString(List.of(
                            new AiConversationTurn("q" + Math.max(1, index - 3), "a" + Math.max(1, index - 3)),
                            new AiConversationTurn("q" + Math.max(1, index - 2), "a" + Math.max(1, index - 2)),
                            new AiConversationTurn("q" + Math.max(1, index - 1), "a" + Math.max(1, index - 1))
                    )));
            historyService.append(1L, "q" + index, "a" + index);
        }

        ArgumentCaptor<String> serializedTurns = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.atLeastOnce()).set(
                eq("ai:guide:conversation:1"), serializedTurns.capture(), eq(Duration.ofMinutes(30))
        );
        List<AiConversationTurn> storedTurns = objectMapper.readValue(
                serializedTurns.getValue(), new TypeReference<>() { }
        );

        assertThat(storedTurns).extracting(AiConversationTurn::question)
                .containsExactly("q2", "q3", "q4");
    }

    @Test
    void loadsOnlyRequestedUsersConversation() throws Exception {
        when(valueOperations.get("ai:guide:conversation:2")).thenReturn(
                objectMapper.writeValueAsString(List.of(new AiConversationTurn("previous question", "previous answer")))
        );

        assertThat(historyService.load(2L))
                .containsExactly(new AiConversationTurn("previous question", "previous answer"));
        verify(valueOperations).get("ai:guide:conversation:2");
    }

    @Test
    void returnsEmptyHistoryForUsersFirstQuestion() {
        when(valueOperations.get("ai:guide:conversation:1")).thenReturn(null);

        assertThat(historyService.load(1L)).isEmpty();
    }

    @Test
    void fallsBackToEmptyHistoryWhenRedisFails() {
        when(valueOperations.get(any())).thenThrow(new IllegalStateException("Redis unavailable"));

        assertThat(historyService.load(1L)).isEmpty();
    }
}
