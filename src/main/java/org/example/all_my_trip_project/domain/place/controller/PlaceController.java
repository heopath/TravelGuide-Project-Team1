package org.example.all_my_trip_project.domain.place.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDetailResult;
import org.example.all_my_trip_project.domain.place.dto.RecentPlaceResult;
import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.example.all_my_trip_project.domain.place.service.PlaceViewHistoryService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
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
    private final PlaceViewHistoryService placeViewHistoryService;

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

    /**
     * 마이페이지 "최근 본 여행지"에 남긴다.
     *
     * <p>상세 조회(GET)에 얹지 않고 따로 받는다. {@code getDetail}은 캐시를 타므로
     * 그 안에서 기록하면 캐시가 맞을 때 빠지고, 조회에 쓰기를 섞으면 나중에 캐시를
     * 손볼 때 위험하다.
     *
     * <p>비로그인은 남길 곳이 없으므로 조용히 넘어간다. 상세 화면은 로그인 없이도
     * 볼 수 있어야 하고, 이력이 없다고 오류를 띄울 이유가 없다.
     */
    @PostMapping("/{placeId}/view")
    public ApiResponse<Void> recordView(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long placeId) {
        if (principal != null) {
            placeViewHistoryService.record(principal.userId(), placeId);
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/recent")
    public ApiResponse<List<RecentPlaceResult>> recent(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) Integer size) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(placeViewHistoryService.findRecent(principal.userId(), size));
    }

    @GetMapping
    public ApiResponse<List<PlaceDTO>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long styleId,
            // 추천장소 화면만 true로 부른다. 기본값이 false여야 카카오 장소 중복 확인처럼
            // 전체를 조회해야 하는 호출이 그대로 동작한다.
            @RequestParam(defaultValue = "false") boolean recommended) {
        Long userId = principal == null ? null : principal.userId();
        List<PlaceDTO> places;
        if ((keyword != null && !keyword.isBlank())
                || (category != null && !category.isBlank())
                || (region != null && !region.isBlank())
                || styleId != null) {
            places = placeService.search(userId, recommended, keyword, category, region, styleId, page, size);
        } else {
            places = placeService.getPage(userId, recommended, page, size);
        }
        return ApiResponse.success(places);
    }
}
