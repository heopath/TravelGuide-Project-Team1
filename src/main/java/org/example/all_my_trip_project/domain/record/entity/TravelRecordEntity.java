package org.example.all_my_trip_project.domain.record.entity;

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
import org.example.all_my_trip_project.domain.record.type.RecordVisibility;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Entity
@Table(name = "travel_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "travel_record_id")
    private Long travelRecordId;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column
    private Short rating;

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private TravelRecordEntity(
            Long tripId,
            Long userId,
            String title,
            String content,
            Short rating,
            RecordVisibility visibility
    ) {
        this.tripId = tripId;
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.rating = rating;
        this.visibility = visibility.name();
    }

    public static TravelRecordEntity create(
            Long tripId,
            Long userId,
            String title,
            String content,
            Short rating,
            RecordVisibility visibility
    ) {
        return new TravelRecordEntity(tripId, userId, title, content, rating, visibility);
    }

    public void updateContent(String title, String content, Short rating, RecordVisibility visibility) {
        this.title = title;
        this.content = content;
        this.rating = rating;
        this.visibility = visibility.name();
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    public boolean isPublic() {
        return RecordVisibility.PUBLIC.name().equals(visibility);
    }

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
