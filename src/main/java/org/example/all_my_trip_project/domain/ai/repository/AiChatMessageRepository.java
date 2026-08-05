package org.example.all_my_trip_project.domain.ai.repository;

import org.example.all_my_trip_project.domain.ai.entity.AiChatMessageEntity;
import org.example.all_my_trip_project.domain.ai.entity.AiChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessageEntity, Long> {

    List<AiChatMessageEntity> findTop6BySessionOrderBySequenceNumberDesc(AiChatSessionEntity session);

    int countBySession(AiChatSessionEntity session);
}
