package org.example.all_my_trip_project.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminReservationPage;
import org.example.all_my_trip_project.domain.admin.service.AdminReservationService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예약 모니터링. 조회만 제공한다.
 *
 * <p>상태 변경은 재고와 함께 움직여야 해서 이 화면에 두지 않는다. 목록에서 개별 행의 상태만
 * 바꾸면 {@code ticket_inventory.reserved_quantity}를 되돌리는 경로가 빠진다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final AdminReservationService adminReservationService;

    @GetMapping
    public ApiResponse<AdminReservationPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean expiredPendingOnly) {
        return ApiResponse.success(
                adminReservationService.list(page, size, status, keyword, expiredPendingOnly));
    }
}
