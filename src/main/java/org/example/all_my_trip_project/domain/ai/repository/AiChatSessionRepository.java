package org.example.all_my_trip_project.domain.ai.repository;

import org.example.all_my_trip_project.domain.ai.entity.AiChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AiChatSessionRepository extends JpaRepository<AiChatSessionEntity, Long> {

    /**
     * Serializes first-session creation for one user/trip pair across application instances.
     * The unique index remains the final integrity guarantee.
     */
    @Query(value = """
            select pg_advisory_xact_lock(
                hashtextextended(cast(:userId as text) || ':' || cast(:tripId as text), 0)
            )
            """, nativeQuery = true)
    Object acquireConversationCreationLock(
            @Param("userId") Long userId,
            @Param("tripId") Long tripId
    );

    Optional<AiChatSessionEntity> findByUserIdAndTripIdAndStatus(Long userId, Long tripId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session from AiChatSessionEntity session
            where session.userId = :userId and session.tripId = :tripId and session.status = :status
            """)
    Optional<AiChatSessionEntity> findActiveForUpdate(
            @Param("userId") Long userId,
            @Param("tripId") Long tripId,
            @Param("status") String status
    );

}
