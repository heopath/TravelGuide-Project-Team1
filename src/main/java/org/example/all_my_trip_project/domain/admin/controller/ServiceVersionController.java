package org.example.all_my_trip_project.domain.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.ServiceVersionDTO;
import org.example.all_my_trip_project.domain.admin.dto.ServiceVersionUpdateRequest;
import org.example.all_my_trip_project.domain.admin.service.ServiceVersionService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/service-settings/footer-version")
@RequiredArgsConstructor
public class ServiceVersionController {

    private final ServiceVersionService serviceVersionService;

    @GetMapping
    public ApiResponse<ServiceVersionDTO> get() {
        return ApiResponse.success(serviceVersionService.get());
    }

    @PutMapping
    public ApiResponse<ServiceVersionDTO> update(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @Valid @RequestBody ServiceVersionUpdateRequest request) {
        ServiceVersionDTO result = serviceVersionService.update(request.version(), admin.userId());
        return ApiResponse.success("푸터 표시 버전을 변경했습니다.", result);
    }
}
