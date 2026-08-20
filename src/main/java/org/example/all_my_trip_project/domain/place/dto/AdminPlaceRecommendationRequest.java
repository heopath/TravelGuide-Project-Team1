package org.example.all_my_trip_project.domain.place.dto;

import jakarta.validation.constraints.NotNull;

/** 추천장소 화면 노출 여부. 데이터 유효성을 뜻하는 active와 별개다. */
public record AdminPlaceRecommendationRequest(@NotNull Boolean recommended) {}
