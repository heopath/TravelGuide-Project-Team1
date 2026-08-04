package org.example.all_my_trip_project.domain.place.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDetailResult;
import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {
    private final PlaceService placeService;

    @PostMapping
    public ResponseEntity<ApiResponse<PlaceDTO>> create(@RequestBody PlaceDTO place) {
        Long id = placeService.create(place);
        return ResponseEntity.created(URI.create("/api/v1/places/" + id))
                .body(ApiResponse.success("장소가 생성되었습니다.", placeService.get(id)));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResult> get(@PathVariable Long placeId) {
        return ApiResponse.success(placeService.getDetail(placeId));
    }

    @GetMapping
    public ApiResponse<List<PlaceDTO>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long styleId) {
        Long userId = principal == null ? null : principal.userId();
        List<PlaceDTO> places;
        if ((keyword != null && !keyword.isBlank())
                || (category != null && !category.isBlank())
                || (region != null && !region.isBlank())
                || styleId != null) {
            places = placeService.search(userId, keyword, category, region, styleId, page, size);
        } else {
            places = placeService.getPage(userId, page, size);
        }
        return ApiResponse.success(places);
    }

    @PutMapping("/{placeId}")
    public ApiResponse<PlaceDTO> update(@PathVariable Long placeId, @RequestBody PlaceDTO place) {
        place.setPlaceId(placeId);
        placeService.update(place);
        return ApiResponse.success("장소 정보가 수정되었습니다.", placeService.get(placeId));
    }

    @DeleteMapping("/{placeId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long placeId) {
        placeService.delete(placeId);
        return ResponseEntity.ok(ApiResponse.success("장소가 삭제되었습니다.", null));
    }
}
