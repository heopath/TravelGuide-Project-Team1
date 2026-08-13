package org.example.all_my_trip_project.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminAuditLogPage;
import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 조작 이력 조회. 읽기 전용이다.
 *
 * <p>수정·삭제 엔드포인트를 두지 않는다. 고칠 수 있는 기록은 기록이 아니다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditService adminAuditService;

    @GetMapping
    public ApiResponse<AdminAuditLogPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Long adminUserId) {
        return ApiResponse.success(
                adminAuditService.list(page, size, actionType, targetType, targetId, adminUserId));
    }
}
