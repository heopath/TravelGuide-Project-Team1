package org.example.all_my_trip_project.domain.review.dto;

import java.math.BigDecimal;
import java.util.Map;

public record PlaceReviewSummary(
        BigDecimal averageRating,
        long reviewCount,
        Map<Integer, Long> ratingDistribution
) {
}
