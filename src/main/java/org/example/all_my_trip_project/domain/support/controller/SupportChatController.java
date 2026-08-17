package org.example.all_my_trip_project.domain.support.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageRequest;
import org.example.all_my_trip_project.domain.support.dto.SupportChatViewResponse;
import org.example.all_my_trip_project.domain.support.service.SupportChatService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 손님 쪽 상담 채팅. {@code /api/v1/support/**}는 이미 로그인을 요구한다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/support/chat")
@RequiredArgsConstructor
public class SupportChatController {

    private final SupportChatService supportChatService;

    /** 상담을 시작하거나 이어간다. 열린 방이 있으면 그 방을 준다. */
    @PostMapping
    public ApiResponse<SupportChatViewResponse> open(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(supportChatService.openMyRoom(requireUserId(principal)));
    }

    /** 폴링으로 새 메시지를 받아 간다. */
    @GetMapping
    public ApiResponse<SupportChatViewResponse> mine(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(supportChatService.myRoom(requireUserId(principal)));
    }

    @PostMapping("/messages")
    public ApiResponse<SupportChatViewResponse> send(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SupportChatMessageRequest request) {
        return ApiResponse.success(
                supportChatService.sendAsUser(requireUserId(principal), request.content()));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
