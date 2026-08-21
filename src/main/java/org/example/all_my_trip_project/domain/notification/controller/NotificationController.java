package org.example.all_my_trip_project.domain.notification.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.notification.dto.NotificationDTO;
import org.example.all_my_trip_project.domain.notification.service.NotificationService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 마이페이지 알림. (#191)
 *
 * <p>남의 알림을 건드리지 못하게 <b>주소에 사용자를 받지 않는다.</b> 로그인한 사람의
 * 알림만 다루고, 읽음 처리도 사용자 번호를 조건에 함께 넣어 고친다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 내 알림 목록.
     *
     * <p>안 읽은 개수를 함께 내린다. 화면이 목록과 배지를 따로 부르면 두 번 오가고,
     * 그 사이에 값이 갈릴 수 있다.
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> mine(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = requireUserId(principal);
        List<NotificationDTO> items = notificationService.findMine(userId, page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", Math.max(page, 0));
        body.put("total", notificationService.countMine(userId));
        body.put("unread", notificationService.countUnread(userId));
        return ApiResponse.success(body);
    }

    /** 헤더 배지처럼 개수만 필요할 때. 목록까지 받으면 낭비다. */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unread", notificationService.countUnread(requireUserId(principal)));
        return ApiResponse.success(body);
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> read(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable @Positive Long notificationId) {
        notificationService.markRead(requireUserId(principal), notificationId);
        return ApiResponse.success("알림을 읽음으로 표시했습니다.", null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        notificationService.markAllRead(requireUserId(principal));
        return ApiResponse.success("알림을 모두 읽음으로 표시했습니다.", null);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
