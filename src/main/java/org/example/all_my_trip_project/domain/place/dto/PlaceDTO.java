package org.example.all_my_trip_project.domain.place.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDTO implements Serializable {
    private Long placeId;
    private String externalProvider;
    private String externalPlaceId;
    private String category;
    private String name;
    private String countryCode;
    private String region;
    private String city;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private String phone;
    private String websiteUrl;
    private BigDecimal averageRating;
    private Boolean active;
    private Boolean favorite;
    // PostgreSQL TIMESTAMPTZ의 UTC offset을 보존하기 위한 타입이며 DTO 필드명은 기존과 동일하다.
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
