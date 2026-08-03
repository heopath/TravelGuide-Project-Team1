package org.example.all_my_trip_project.domain.favorite.dto;

public record FavoriteStatusResponse(
        Long placeId,
        boolean favorite
) {
}
