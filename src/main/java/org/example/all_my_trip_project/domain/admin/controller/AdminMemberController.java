package org.example.all_my_trip_project.domain.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberDTO;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberPage;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberRoleRequest;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberStatusRequest;
import org.example.all_my_trip_project.domain.admin.service.AdminMemberService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/admin/**}는 {@code ApiSecurityConfig}에서 이미 {@code ROLE_ADMIN}을
 * 요구한다. 여기서 다시 걸지 않는다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ApiResponse<AdminMemberPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role) {
        return ApiResponse.success(adminMemberService.list(page, size, keyword, status, role));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminMemberDTO> changeStatus(
            @PathVariable Long userId,
            @Valid @RequestBody AdminMemberStatusRequest request) {
        AdminMemberDTO member = adminMemberService.changeStatus(userId, request.status(), request.reason());
        return ApiResponse.success(
                "SUSPENDED".equals(member.getStatus()) ? "회원을 정지했습니다." : "회원 정지를 해제했습니다.",
                member);
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<AdminMemberDTO> changeRole(
            @PathVariable Long userId,
            @Valid @RequestBody AdminMemberRoleRequest request) {
        AdminMemberDTO member = adminMemberService.changeRole(userId, request.role(), request.reason());
        return ApiResponse.success(
                "ADMIN".equals(member.getRole()) ? "관리자로 승격했습니다." : "관리자 권한을 해제했습니다.",
                member);
    }
}
