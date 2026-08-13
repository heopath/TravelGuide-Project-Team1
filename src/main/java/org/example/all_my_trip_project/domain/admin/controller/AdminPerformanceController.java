package org.example.all_my_trip_project.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminPerformanceDTO;
import org.example.all_my_trip_project.domain.admin.service.AdminPerformanceService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/performance")
@RequiredArgsConstructor
public class AdminPerformanceController {

    private final AdminPerformanceService adminPerformanceService;

    @GetMapping
    public ApiResponse<AdminPerformanceDTO> performance() {
        return ApiResponse.success(adminPerformanceService.collect());
    }
}
