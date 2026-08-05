package org.example.all_my_trip_project.domain.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(name = "ai_chat_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChatSessionEntity {

    public static final String ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_chat_session_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private AiChatSessionEntity(Long userId, Long tripId) {
        this.userId = userId;
        this.tripId = tripId;
        this.status = ACTIVE;
        touch();
    }

    public static AiChatSessionEntity active(Long userId, Long tripId) {
        return new AiChatSessionEntity(userId, tripId);
    }

    public void touch() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        this.updatedAt = now;
        this.lastMessageAt = now;
    }
}
