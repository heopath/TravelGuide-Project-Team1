package org.example.all_my_trip_project.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.entity.AiChatSessionEntity;
import org.example.all_my_trip_project.domain.ai.repository.AiChatSessionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps an insert conflict from marking the message-writing transaction as rollback-only.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
class AiChatSessionCreationService {

    private final AiChatSessionRepository sessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiChatSessionEntity create(Long userId, Long tripId) {
        return sessionRepository.saveAndFlush(AiChatSessionEntity.active(userId, tripId));
    }
}
