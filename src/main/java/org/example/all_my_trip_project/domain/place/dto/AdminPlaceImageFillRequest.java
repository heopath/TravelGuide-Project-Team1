package org.example.all_my_trip_project.domain.place.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 목록에서 고른 장소들의 대표 이미지를 찾아 채운다. */
public record AdminPlaceImageFillRequest(
        // 장소마다 외부 API를 한 번씩 부른다. 화면 한 페이지(100건)를 상한으로 둔다.
        @NotEmpty @Size(max = 100) List<Long> placeIds
) {}
