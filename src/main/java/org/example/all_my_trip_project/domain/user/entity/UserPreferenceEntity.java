package org.example.all_my_trip_project.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(name = "user_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_preference_id")
    private Long userPreferenceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "travel_style_id", nullable = false)
    private Short travelStyleId;

    @Column(name = "preference_score", nullable = false)
    private Short preferenceScore;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private UserPreferenceEntity(
            Long userId,
            Short travelStyleId,
            Short preferenceScore
    ) {
        this.userId = userId;
        this.travelStyleId = travelStyleId;
        this.preferenceScore = preferenceScore;
        this.source = "EXPLICIT";
    }

    public static UserPreferenceEntity explicit(
            Long userId,
            Short travelStyleId,
            Short preferenceScore
    ) {
        return new UserPreferenceEntity(
                userId,
                travelStyleId,
                preferenceScore
        );
    }

    public void replaceWithExplicitScore(Short preferenceScore) {
        this.preferenceScore = preferenceScore;
        this.source = "EXPLICIT";
    }

    public boolean isExplicit() {
        return "EXPLICIT".equals(source);
    }

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
