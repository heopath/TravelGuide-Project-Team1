package org.example.all_my_trip_project.domain.place.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.AdminPlacePage;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceRecommendationBulkRequest;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceRecommendationRequest;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceRequest;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceVisibilityRequest;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageBackfillResult;
import org.example.all_my_trip_project.domain.place.service.AdminPlaceService;
import org.example.all_my_trip_project.domain.place.service.PlaceImageBackfillService;
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
    private final PlaceImageBackfillService placeImageBackfillService;

    /**
     * 대표 이미지가 비어 있는 기존 장소를 한 묶음씩 채운다.
     *
     * <p>장소마다 외부 API를 한 번씩 부르므로 한 요청에서 전부 처리하지 않는다. 응답의
     * {@code nextAfter}를 {@code after}로 넘겨 {@code done}이 될 때까지 이어서 부른다.
     */
    @PostMapping("/images/backfill")
    public ApiResponse<PlaceImageBackfillResult> backfillImages(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(required = false) Integer size) {
        PlaceImageBackfillResult result = placeImageBackfillService.backfill(after, size);
        return ApiResponse.success(
                "장소 " + result.scanned() + "곳을 확인해 " + result.filled() + "곳에 대표 이미지를 넣었습니다.",
                result);
    }

    @GetMapping
    public ApiResponse<AdminPlacePage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean recommended) {
        return ApiResponse.success(adminPlaceService.list(page, size, keyword, category, recommended));
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
        // 추천 노출은 is_recommended가 맡는다. 여기는 장소 데이터 자체의 사용 여부다.
        return ApiResponse.success(request.active() ? "장소를 사용 처리했습니다." : "장소를 미사용 처리했습니다.",
                adminPlaceService.setVisibility(placeId, request.active()));
    }

    @PatchMapping("/recommendation")
    public ApiResponse<Integer> recommendationBulk(
            @Valid @RequestBody AdminPlaceRecommendationBulkRequest request) {
        int changed = adminPlaceService.setRecommendedAll(request.placeIds(), request.recommended());
        String message = "선택한 장소 " + changed
                + (request.recommended() ? "곳을 추천장소에 노출합니다." : "곳을 추천장소에서 내렸습니다.");
        return ApiResponse.success(message, changed);
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
