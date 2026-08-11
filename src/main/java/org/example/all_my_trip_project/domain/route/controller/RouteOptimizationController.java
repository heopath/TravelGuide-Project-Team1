package org.example.all_my_trip_project.domain.route.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.route.dto.RouteOptimizationResponse;
import org.example.all_my_trip_project.domain.route.service.RouteOptimizationService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@Profile("!ui")
@RequiredArgsConstructor
public class RouteOptimizationController {
    private final RouteOptimizationService routeOptimizationService;

    @PostMapping("/api/v1/trip-days/{tripDayId}/optimize-route")
    public ApiResponse<RouteOptimizationResponse> optimize(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripDayId) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success("이동시간 기준으로 동선을 최적화했습니다.",
                routeOptimizationService.optimize(principal.userId(), tripDayId));
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
