package org.example.all_my_trip_project.domain.support.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryPage;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryRequest;
import org.example.all_my_trip_project.domain.support.service.SupportService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {
    private final SupportService supportService;

    @PostMapping("/inquiries")
    public ResponseEntity<ApiResponse<SupportInquiryDTO>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SupportInquiryRequest request) {
        SupportInquiryDTO inquiry = supportService.create(requireUserId(principal), request);
        return ResponseEntity.created(URI.create("/api/v1/support/inquiries/" + inquiry.getSupportInquiryId()))
                .body(ApiResponse.success("문의가 접수되었습니다.", inquiry));
    }

    @GetMapping("/inquiries/me")
    public ApiResponse<SupportInquiryPage> mine(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(supportService.getMine(requireUserId(principal), page, size));
    }

    @GetMapping("/inquiries/{inquiryId}")
    public ApiResponse<SupportInquiryDTO> detail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long inquiryId) {
        return ApiResponse.success(supportService.getMineDetail(requireUserId(principal), inquiryId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
