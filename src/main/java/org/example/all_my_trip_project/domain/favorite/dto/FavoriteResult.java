package org.example.all_my_trip_project.domain.favorite.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteResult {
    private Long favoriteId;
    private Long userId;
    private Long placeId;
    private String memo;
    private OffsetDateTime createdAt;
    private String placeName;
    private String category;
    private String region;
    private String primaryImageUrl;
}
