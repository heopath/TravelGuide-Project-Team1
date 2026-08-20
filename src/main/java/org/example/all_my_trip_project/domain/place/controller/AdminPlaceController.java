package org.example.all_my_trip_project.domain.place.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.AdminPlacePage;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceRecommendationRequest;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceRequest;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceVisibilityRequest;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.service.AdminPlaceService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController {

    private final AdminPlaceService adminPlaceService;

    @GetMapping
    public ApiResponse<AdminPlacePage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active) {
        return ApiResponse.success(adminPlaceService.list(page, size, keyword, category, active));
    }

    @PostMapping
    public ApiResponse<PlaceDTO> create(@Valid @RequestBody AdminPlaceRequest request) {
        return ApiResponse.success("추천 장소를 등록했습니다.", adminPlaceService.create(request));
    }

    @PutMapping("/{placeId}")
    public ApiResponse<PlaceDTO> update(
            @PathVariable Long placeId,
            @Valid @RequestBody AdminPlaceRequest request) {
        return ApiResponse.success("추천 장소를 수정했습니다.", adminPlaceService.update(placeId, request));
    }

    @PatchMapping("/{placeId}/visibility")
    public ApiResponse<PlaceDTO> visibility(
            @PathVariable Long placeId,
            @Valid @RequestBody AdminPlaceVisibilityRequest request) {
        return ApiResponse.success(request.active() ? "추천 장소를 공개했습니다." : "추천 장소를 숨겼습니다.",
                adminPlaceService.setVisibility(placeId, request.active()));
    }

    @PatchMapping("/{placeId}/recommendation")
    public ApiResponse<PlaceDTO> recommendation(
            @PathVariable Long placeId,
            @Valid @RequestBody AdminPlaceRecommendationRequest request) {
        return ApiResponse.success(
                request.recommended() ? "추천장소에 노출합니다." : "추천장소에서 내렸습니다.",
                adminPlaceService.setRecommended(placeId, request.recommended()));
    }
}
