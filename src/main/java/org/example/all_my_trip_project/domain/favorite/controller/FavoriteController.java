package org.example.all_my_trip_project.domain.favorite.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteResult;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteStatusResponse;
import org.example.all_my_trip_project.domain.favorite.service.FavoriteService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.net.URI;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteResult>> add(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam Long placeId,
            @RequestParam(required = false) String memo) {
        FavoriteResult favorite = favoriteService.add(requireUserId(principal), placeId, memo);
        return ResponseEntity.created(URI.create("/api/v1/favorites/" + placeId))
                .body(ApiResponse.success("찜한 장소에 추가했습니다.", favorite));
    }

    @GetMapping
    public ApiResponse<List<FavoriteResult>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(favoriteService.getFavorites(requireUserId(principal), page, size));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<FavoriteStatusResponse> status(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long placeId) {
        boolean favorite = favoriteService.isFavorite(requireUserId(principal), placeId);
        return ApiResponse.success(new FavoriteStatusResponse(placeId, favorite));
    }

    @GetMapping("/count")
    public ApiResponse<Long> count(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(favoriteService.countFavorites(requireUserId(principal)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> remove(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam Long placeId) {
        favoriteService.remove(requireUserId(principal), placeId);
        return ResponseEntity.ok(ApiResponse.success("찜을 해제했습니다.", null));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
