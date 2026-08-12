package org.example.all_my_trip_project.domain.support.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.AdminSupportInquiryDetail;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryPage;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyRequest;
import org.example.all_my_trip_project.domain.support.dto.SupportStatusRequest;
import org.example.all_my_trip_project.domain.support.service.AdminSupportService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/support/inquiries")
@RequiredArgsConstructor
public class AdminSupportController {
    private static final String ADMIN_ROLE = "ADMIN";
    private final AdminSupportService service;

    @GetMapping
    public ApiResponse<SupportInquiryPage> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(principal);
        return ApiResponse.success(service.getPage(status, page, size));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<AdminSupportInquiryDetail> detail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long inquiryId) {
        requireAdmin(principal);
        return ApiResponse.success(service.getDetail(inquiryId));
    }

    @PostMapping("/{inquiryId}/replies")
    public ApiResponse<AdminSupportInquiryDetail> reply(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long inquiryId,
            @Valid @RequestBody SupportReplyRequest request) {
        requireAdmin(principal);
        return ApiResponse.success("문의 답변이 등록되었습니다.", service.reply(principal.userId(), inquiryId, request));
    }

    @PatchMapping("/{inquiryId}/status")
    public ApiResponse<AdminSupportInquiryDetail> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long inquiryId,
            @Valid @RequestBody SupportStatusRequest request) {
        requireAdmin(principal);
        return ApiResponse.success("문의 상태가 변경되었습니다.", service.updateStatus(inquiryId, request.status()));
    }

    private void requireAdmin(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (!ADMIN_ROLE.equals(principal.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
