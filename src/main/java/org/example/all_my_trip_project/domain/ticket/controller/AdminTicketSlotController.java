package org.example.all_my_trip_project.domain.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketInventoryRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotStatusRequest;
import org.example.all_my_trip_project.domain.ticket.service.AdminTicketProductService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시간대 재고 조정.
 *
 * <p>상품이 아니라 시간대가 대상이라 주소를 상품 아래에 두지 않았다. 한 시간대는 옵션에
 * 속하고 옵션은 상품에 속하지만, 조정할 때 필요한 것은 시간대 id 하나뿐이다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/ticket-slots")
@RequiredArgsConstructor
public class AdminTicketSlotController {

    private final AdminTicketProductService adminTicketProductService;

    @PatchMapping("/{slotId}/inventory")
    public ApiResponse<AdminTicketSlotDTO> changeInventory(
            @PathVariable Long slotId,
            @Valid @RequestBody AdminTicketInventoryRequest request) {
        return ApiResponse.success("재고를 변경했습니다.",
                adminTicketProductService.changeInventory(slotId, request.totalQuantity()));
    }

    /**
     * 시간대를 열거나 닫는다. 삭제는 없다 — 예약이 시간대를 참조하고 있어 지우면
     * 이미 팔린 예약이 무엇이었는지 되짚을 수 없다.
     */
    @PatchMapping("/{slotId}/status")
    public ApiResponse<AdminTicketSlotDTO> changeStatus(
            @PathVariable Long slotId,
            @Valid @RequestBody AdminTicketSlotStatusRequest request) {
        return ApiResponse.success("시간대 상태를 변경했습니다.",
                adminTicketProductService.changeSlotStatus(slotId, request.status()));
    }
}
