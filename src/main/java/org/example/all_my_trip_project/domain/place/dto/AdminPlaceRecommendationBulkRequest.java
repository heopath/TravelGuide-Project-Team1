package org.example.all_my_trip_project.domain.place.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 목록에서 여러 장소를 골라 한 번에 추천 노출을 켜거나 끈다. */
public record AdminPlaceRecommendationBulkRequest(
        // 한 번에 너무 많이 보내면 트랜잭션이 길어진다. 화면 한 페이지(100건)를 상한으로 둔다.
        @NotEmpty @Size(max = 100) List<Long> placeIds,
        @NotNull Boolean recommended
) {}
