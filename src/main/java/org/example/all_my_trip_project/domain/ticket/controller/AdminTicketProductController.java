package org.example.all_my_trip_project.domain.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductPage;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductStatusRequest;
import org.example.all_my_trip_project.domain.ticket.service.AdminTicketProductService;
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

import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/ticket-products")
@RequiredArgsConstructor
public class AdminTicketProductController {

    private final AdminTicketProductService adminTicketProductService;

    @GetMapping
    public ApiResponse<AdminTicketProductPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminTicketProductService.list(page, size, keyword, status));
    }

    @PatchMapping("/{ticketProductId}/status")
    public ApiResponse<AdminTicketProductDTO> changeStatus(
            @PathVariable Long ticketProductId,
            @Valid @RequestBody AdminTicketProductStatusRequest request) {
        return ApiResponse.success("판매 상태를 변경했습니다.",
                adminTicketProductService.changeStatus(ticketProductId, request.status()));
    }

    @PostMapping
    public ApiResponse<AdminTicketProductDTO> create(
            @Valid @RequestBody AdminTicketProductRequest request) {
        return ApiResponse.success("예약 상품을 등록했습니다. 판매하려면 옵션과 시간대를 추가한 뒤 상태를 바꿔주세요.",
                adminTicketProductService.create(request));
    }

    @PutMapping("/{ticketProductId}")
    public ApiResponse<AdminTicketProductDTO> update(
            @PathVariable Long ticketProductId,
            @Valid @RequestBody AdminTicketProductRequest request) {
        return ApiResponse.success("예약 상품을 수정했습니다.",
                adminTicketProductService.update(ticketProductId, request));
    }

    @GetMapping("/{ticketProductId}/slots")
    public ApiResponse<List<AdminTicketSlotDTO>> slots(@PathVariable Long ticketProductId) {
        return ApiResponse.success(adminTicketProductService.listSlots(ticketProductId));
    }
}
