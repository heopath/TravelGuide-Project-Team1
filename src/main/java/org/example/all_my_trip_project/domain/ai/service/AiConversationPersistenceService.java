package org.example.all_my_trip_project.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.entity.AiChatMessageEntity;
import org.example.all_my_trip_project.domain.ai.entity.AiChatSessionEntity;
import org.example.all_my_trip_project.domain.ai.repository.AiChatMessageRepository;
import org.example.all_my_trip_project.domain.ai.repository.AiChatSessionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class AiConversationPersistenceService {

    private static final int MAX_TURNS = 3;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiChatSessionCreationService sessionCreationService;

    @Transactional(readOnly = true)
    public List<AiConversationTurn> loadRecentTurns(Long userId, Long tripId) {
        if (userId == null || tripId == null) {
            return List.of();
        }

        return sessionRepository.findByUserIdAndTripIdAndStatus(userId, tripId, AiChatSessionEntity.ACTIVE)
                .map(this::loadTurns)
                .orElseGet(List::of);
    }

    @Transactional
    public void append(Long userId, Long tripId, String question, String answer) {
        if (userId == null || tripId == null || question == null || answer == null) {
            return;
        }

        sessionRepository.acquireConversationCreationLock(userId, tripId);
        AiChatSessionEntity session = sessionRepository
                .findActiveForUpdate(userId, tripId, AiChatSessionEntity.ACTIVE)
                .orElseGet(() -> createOrFindSession(userId, tripId));
        int nextSequence = messageRepository.countBySession(session) + 1;
        messageRepository.saveAll(List.of(
                AiChatMessageEntity.user(session, question, nextSequence),
                AiChatMessageEntity.assistant(session, answer, nextSequence + 1)
        ));
        session.touch();
    }

    private AiChatSessionEntity createOrFindSession(Long userId, Long tripId) {
        try {
            return sessionCreationService.create(userId, tripId);
        } catch (DataIntegrityViolationException exception) {
            return sessionRepository
                    .findActiveForUpdate(userId, tripId, AiChatSessionEntity.ACTIVE)
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional
    public void archiveActiveSession(Long userId, Long tripId) {
        if (userId == null || tripId == null) {
            return;
        }
        sessionRepository.findActiveForUpdate(userId, tripId, AiChatSessionEntity.ACTIVE)
                .ifPresent(AiChatSessionEntity::archive);
    }

    private List<AiConversationTurn> loadTurns(AiChatSessionEntity session) {
        List<AiChatMessageEntity> messages = new ArrayList<>(
                messageRepository.findTop6BySessionOrderBySequenceNumberDesc(session)
        );
        messages.sort(Comparator.comparingInt(AiChatMessageEntity::getSequenceNumber));

        List<AiConversationTurn> turns = new ArrayList<>();
        for (int index = 0; index + 1 < messages.size(); index++) {
            AiChatMessageEntity question = messages.get(index);
            AiChatMessageEntity answer = messages.get(index + 1);
            if (AiChatMessageEntity.USER.equals(question.getRole())
                    && AiChatMessageEntity.ASSISTANT.equals(answer.getRole())) {
                turns.add(new AiConversationTurn(question.getContent(), answer.getContent()));
                index++;
            }
        }
        return turns.size() <= MAX_TURNS ? turns : turns.subList(turns.size() - MAX_TURNS, turns.size());
    }
}
