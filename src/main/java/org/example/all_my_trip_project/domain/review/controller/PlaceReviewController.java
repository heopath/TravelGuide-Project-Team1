package org.example.all_my_trip_project.domain.review.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewDTO;
import org.example.all_my_trip_project.domain.review.dto.MyPlaceReviewPage;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewPage;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRequest;
import org.example.all_my_trip_project.domain.review.service.PlaceReviewService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlaceReviewController {
    private final PlaceReviewService reviewService;

    @GetMapping("/members/me/place-reviews")
    public ApiResponse<MyPlaceReviewPage> myReviews(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(
                reviewService.getMyPage(requireUserId(principal), page, size)
        );
    }

    @GetMapping("/places/{placeId}/reviews")
    public ApiResponse<PlaceReviewPage> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        Long userId = principal == null ? null : principal.userId();
        return ApiResponse.success(reviewService.getPage(placeId, userId, page, size));
    }

    @PostMapping("/places/{placeId}/reviews")
    public ResponseEntity<ApiResponse<PlaceReviewDTO>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long placeId,
            @Valid @RequestBody PlaceReviewRequest request) {
        PlaceReviewDTO review = reviewService.create(requireUserId(principal), placeId, request);
        return ResponseEntity.created(URI.create("/api/v1/place-reviews/" + review.getPlaceReviewId()))
                .body(ApiResponse.success("장소 후기를 등록했습니다.", review));
    }

    @PatchMapping("/place-reviews/{reviewId}")
    public ApiResponse<PlaceReviewDTO> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody PlaceReviewRequest request) {
        return ApiResponse.success(
                "장소 후기를 수정했습니다.",
                reviewService.update(requireUserId(principal), reviewId, request)
        );
    }

    @DeleteMapping("/place-reviews/{reviewId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reviewId) {
        reviewService.delete(requireUserId(principal), reviewId);
        return ApiResponse.success("장소 후기를 삭제했습니다.", null);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
