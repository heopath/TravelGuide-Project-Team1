package org.example.all_my_trip_project.domain.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(name = "ai_chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChatMessageEntity {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_chat_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_chat_session_id", nullable = false)
    private AiChatSessionEntity session;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private AiChatMessageEntity(AiChatSessionEntity session, String role, String content, int sequenceNumber) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.sequenceNumber = sequenceNumber;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static AiChatMessageEntity user(AiChatSessionEntity session, String content, int sequenceNumber) {
        return new AiChatMessageEntity(session, USER, content, sequenceNumber);
    }

    public static AiChatMessageEntity assistant(AiChatSessionEntity session, String content, int sequenceNumber) {
        return new AiChatMessageEntity(session, ASSISTANT, content, sequenceNumber);
    }
}
