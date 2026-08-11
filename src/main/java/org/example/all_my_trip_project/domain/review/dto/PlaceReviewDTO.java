package org.example.all_my_trip_project.domain.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceReviewDTO {
    private Long placeReviewId;
    private Long placeId;
    private String placeName;
    private String placeCategory;
    private String placeAddress;
    private String placeImageUrl;
    private Long userId;
    private String nickname;
    private Short rating;
    private String content;
    private Boolean verifiedVisit;
    private Boolean ownedByRequester;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
