package org.example.all_my_trip_project.domain.route.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.route.dto.RouteOptimizationResponse;
import org.example.all_my_trip_project.domain.route.dto.TransitRouteRequest;
import org.example.all_my_trip_project.domain.route.dto.TransitRouteResponse;
import org.example.all_my_trip_project.domain.route.service.RouteOptimizationService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@Profile("!ui")
@RequiredArgsConstructor
public class RouteOptimizationController {
    private final RouteOptimizationService routeOptimizationService;

    @PostMapping("/api/v1/routes/transit")
    public ApiResponse<TransitRouteResponse> searchTransitRoute(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TransitRouteRequest request) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success("대중교통 경로를 조회했습니다.",
                routeOptimizationService.searchTransitRoute(request));
    }

    @PostMapping("/api/v1/routes/walk")
    public ApiResponse<TransitRouteResponse> searchWalkingRoute(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TransitRouteRequest request) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success("도보 경로를 조회했습니다.",
                routeOptimizationService.searchWalkingRoute(request));
    }

    @PostMapping("/api/v1/routes/car")
    public ApiResponse<TransitRouteResponse> searchDrivingRoute(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TransitRouteRequest request) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success("자동차 경로를 조회했습니다.",
                routeOptimizationService.searchDrivingRoute(request));
    }

    @PostMapping("/api/v1/trip-days/{tripDayId}/optimize-route")
    public ApiResponse<RouteOptimizationResponse> optimize(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripDayId,
            @RequestParam(defaultValue = "TIME") String criterion,
            @RequestParam(defaultValue = "CAR") String mode) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        String normalizedCriterion = criterion == null ? "TIME" : criterion.trim().toUpperCase();
        String message = "DISTANCE".equals(normalizedCriterion)
                ? "이동거리 우선으로 동선을 최적화했습니다."
                : "이동시간 우선으로 동선을 최적화했습니다.";
        return ApiResponse.success(message,
                routeOptimizationService.optimize(principal.userId(), tripDayId, normalizedCriterion, mode));
    }

    @PostMapping("/api/v1/trip-days/{tripDayId}/items/reorder")
    public ApiResponse<Void> reorder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripDayId,
            @RequestBody List<Long> itemIds) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        routeOptimizationService.reorder(principal.userId(), tripDayId, itemIds);
        return ApiResponse.success("일정 순서를 저장했습니다.", null);
    }
}
