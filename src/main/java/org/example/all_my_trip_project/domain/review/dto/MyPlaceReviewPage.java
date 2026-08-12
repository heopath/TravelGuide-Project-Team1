package org.example.all_my_trip_project.domain.review.dto;

import java.util.List;

public record MyPlaceReviewPage(
        List<PlaceReviewDTO> reviews,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
