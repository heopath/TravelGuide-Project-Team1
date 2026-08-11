package org.example.all_my_trip_project.domain.review.dto;

import java.util.List;

public record PlaceReviewPage(
        PlaceReviewSummary summary,
        List<PlaceReviewDTO> reviews,
        PlaceReviewDTO myReview,
        boolean authenticated,
        int page,
        int size,
        boolean hasNext
) {
}
