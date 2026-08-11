package org.example.all_my_trip_project.domain.place.dto;

import java.util.List;

public record AdminPlacePage(
        List<PlaceDTO> items,
        int page,
        int size,
        long total,
        int totalPages
) {}
