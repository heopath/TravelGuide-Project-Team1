package org.example.all_my_trip_project.domain.place.dto;

import jakarta.validation.constraints.NotNull;

public record AdminPlaceVisibilityRequest(@NotNull Boolean active) {}
