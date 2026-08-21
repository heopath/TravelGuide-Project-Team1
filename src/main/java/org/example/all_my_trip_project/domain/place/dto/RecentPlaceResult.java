package org.example.all_my_trip_project.domain.place.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 마이페이지 "최근 본 여행지" 한 칸. 카드를 그리는 데 필요한 만큼만 담는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecentPlaceResult {
    private Long placeId;
    private String placeName;
    private String category;
    private String region;
    private String primaryImageUrl;
    private OffsetDateTime viewedAt;
}
