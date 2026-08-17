package org.example.all_my_trip_project.domain.support.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageRequest;
import org.example.all_my_trip_project.domain.support.dto.SupportChatRoomDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatViewResponse;
import org.example.all_my_trip_project.domain.support.service.SupportChatService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/v1/admin/**}는 {@code ApiSecurityConfig}에서 이미 {@code ROLE_ADMIN}을 요구한다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/support-chats")
@RequiredArgsConstructor
public class AdminSupportChatController {

    private final SupportChatService supportChatService;

    @GetMapping
    public ApiResponse<List<SupportChatRoomDTO>> rooms(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.success(
                supportChatService.rooms(requireUserId(principal), status, keyword, limit));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<SupportChatViewResponse> room(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long roomId) {
        return ApiResponse.success(supportChatService.room(requireUserId(principal), roomId));
    }

    /** 내가 응대한다. 봇이 응대하던 방도 여기서 사람에게 넘어온다. */
    @PostMapping("/{roomId}/takeover")
    public ApiResponse<SupportChatViewResponse> takeover(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long roomId) {
        return ApiResponse.success("이 상담을 맡았습니다.",
                supportChatService.takeover(requireUserId(principal), roomId));
    }

    @PostMapping("/{roomId}/messages")
    public ApiResponse<SupportChatViewResponse> send(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long roomId,
            @Valid @RequestBody SupportChatMessageRequest request) {
        return ApiResponse.success(
                supportChatService.sendAsAdmin(requireUserId(principal), roomId, request.content()));
    }

    @PostMapping("/{roomId}/close")
    public ApiResponse<SupportChatViewResponse> close(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long roomId) {
        return ApiResponse.success("상담을 종료했습니다.",
                supportChatService.close(requireUserId(principal), roomId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
