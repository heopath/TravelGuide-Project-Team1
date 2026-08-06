package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AiConversationHistoryServiceTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    @Mock
    private ObjectProvider<AiConversationPersistenceService> persistenceServiceProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<String>> fakeRedisLists = new HashMap<>();
    private AiConversationHistoryService historyService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenAnswer(invocation ->
                List.copyOf(fakeRedisLists.getOrDefault(invocation.getArgument(0), List.of()))
        );
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    String serializedTurn = invocation.getArgument(2);
                    List<String> turns = fakeRedisLists.computeIfAbsent(keys.getFirst(), ignored -> new ArrayList<>());
                    turns.add(serializedTurn);
                    while (turns.size() > 3) {
                        turns.removeFirst();
                    }
                    return 1L;
                });
        historyService = new AiConversationHistoryService(redisTemplateProvider, persistenceServiceProvider);
    }

    @Test
    void storesOnlyThreeRecentTurnsWithThirtyMinuteTtl() {
        for (int index = 1; index <= 4; index++) {
            historyService.append(1L, "q" + index, "a" + index);
        }

        assertThat(historyService.load(1L))
                .extracting(AiConversationTurn::question)
                .containsExactly("q2", "q3", "q4");

        ArgumentCaptor<DefaultRedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redisTemplate, times(4)).execute(
                scriptCaptor.capture(),
                eq(List.of("ai:guide:conversation:1")),
                anyString(),
                eq("1800")
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("RPUSH", "LTRIM", "EXPIRE");
    }

    @Test
    void loadsOnlyRequestedUsersConversation() throws Exception {
        fakeRedisLists.put("ai:guide:conversation:2", List.of(
                objectMapper.writeValueAsString(new AiConversationTurn("previous question", "previous answer"))
        ));

        assertThat(historyService.load(2L))
                .containsExactly(new AiConversationTurn("previous question", "previous answer"));
        assertThat(historyService.load(1L)).isEmpty();
    }

    @Test
    void returnsEmptyHistoryForUsersFirstQuestion() {
        assertThat(historyService.load(1L)).isEmpty();
    }

    @Test
    void fallsBackToEmptyHistoryWhenRedisFails() {
        when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        assertThat(historyService.load(1L)).isEmpty();
    }

    @Test
    void loadsDatabaseHistoryWhenRedisHistoryIsEmpty() {
        AiConversationPersistenceService persistenceService = mock(AiConversationPersistenceService.class);
        when(persistenceServiceProvider.getIfAvailable()).thenReturn(persistenceService);
        when(persistenceService.loadRecentTurns(1L, 11L))
                .thenReturn(List.of(new AiConversationTurn("older question", "older answer")));

        assertThat(historyService.load(1L, 11L))
                .containsExactly(new AiConversationTurn("older question", "older answer"));
    }

    @Test
    void storesConversationInDatabaseWhenRedisIsUnavailable() {
        AiConversationPersistenceService persistenceService = mock(AiConversationPersistenceService.class);
        when(persistenceServiceProvider.getIfAvailable()).thenReturn(persistenceService);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

        historyService.append(1L, 11L, "question", "answer");

        verify(persistenceService).append(1L, 11L, "question", "answer");
    }

    @Test
    void deletesRedisAndDatabaseHistoryForTheRequestedTrip() {
        AiConversationPersistenceService persistenceService = mock(AiConversationPersistenceService.class);
        when(persistenceServiceProvider.getIfAvailable()).thenReturn(persistenceService);

        historyService.delete(1L, 11L);

        verify(redisTemplate).delete("ai:guide:conversation:1:trip:11");
        verify(persistenceService).delete(1L, 11L);
    }
}
