package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.entity.AiChatMessageEntity;
import org.example.all_my_trip_project.domain.ai.entity.AiChatSessionEntity;
import org.example.all_my_trip_project.domain.ai.repository.AiChatMessageRepository;
import org.example.all_my_trip_project.domain.ai.repository.AiChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationPersistenceServiceTest {

    private final AiChatSessionRepository sessionRepository = mock(AiChatSessionRepository.class);
    private final AiChatMessageRepository messageRepository = mock(AiChatMessageRepository.class);
    private final AiConversationPersistenceService service =
            new AiConversationPersistenceService(sessionRepository, messageRepository);

    @Test
    void savesQuestionAndAnswerForTheUsersTrip() {
        AiChatSessionEntity session = AiChatSessionEntity.active(1L, 12L);
        when(sessionRepository.findActiveForUpdate(1L, 12L, AiChatSessionEntity.ACTIVE))
                .thenReturn(Optional.of(session));
        when(messageRepository.countBySession(session)).thenReturn(4);

        service.append(1L, 12L, "question", "answer");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessageEntity>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue())
                .extracting(AiChatMessageEntity::getRole, AiChatMessageEntity::getContent,
                        AiChatMessageEntity::getSequenceNumber)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AiChatMessageEntity.USER, "question", 5),
                        org.assertj.core.groups.Tuple.tuple(AiChatMessageEntity.ASSISTANT, "answer", 6)
                );
    }

    @Test
    void loadsOnlyRecentCompleteTurnsFromTheRequestedUsersTrip() {
        AiChatSessionEntity session = AiChatSessionEntity.active(1L, 12L);
        when(sessionRepository.findByUserIdAndTripIdAndStatus(1L, 12L, AiChatSessionEntity.ACTIVE))
                .thenReturn(Optional.of(session));
        when(messageRepository.findTop6BySessionOrderBySequenceNumberDesc(session)).thenReturn(List.of(
                AiChatMessageEntity.assistant(session, "answer-2", 4),
                AiChatMessageEntity.user(session, "question-2", 3),
                AiChatMessageEntity.assistant(session, "answer-1", 2),
                AiChatMessageEntity.user(session, "question-1", 1)
        ));

        assertThat(service.loadRecentTurns(1L, 12L)).containsExactly(
                new AiConversationTurn("question-1", "answer-1"),
                new AiConversationTurn("question-2", "answer-2")
        );
        verify(sessionRepository).findByUserIdAndTripIdAndStatus(1L, 12L, AiChatSessionEntity.ACTIVE);
    }

    @Test
    void deletesOnlyTheRequestedUsersActiveConversation() {
        service.delete(1L, 12L);

        verify(sessionRepository).deleteByUserIdAndTripIdAndStatus(1L, 12L, AiChatSessionEntity.ACTIVE);
    }
}
