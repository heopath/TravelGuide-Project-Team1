package org.example.all_my_trip_project.domain.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(name = "travel_record_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "travel_record_image_id")
    private Long travelRecordImageId;

    @Column(name = "travel_record_id", nullable = false)
    private Long travelRecordId;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_cover", nullable = false)
    private Boolean cover;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private TravelRecordImageEntity(
            Long travelRecordId,
            String imageUrl,
            String altText,
            int sortOrder,
            boolean cover
    ) {
        this.travelRecordId = travelRecordId;
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.sortOrder = sortOrder;
        this.cover = cover;
    }

    public static TravelRecordImageEntity of(
            Long travelRecordId,
            String imageUrl,
            String altText,
            int sortOrder,
            boolean cover
    ) {
        return new TravelRecordImageEntity(travelRecordId, imageUrl, altText, sortOrder, cover);
    }

    @PrePersist
    private void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
