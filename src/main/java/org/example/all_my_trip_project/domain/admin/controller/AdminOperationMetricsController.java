package org.example.all_my_trip_project.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminOperationMetricsDTO;
import org.example.all_my_trip_project.domain.admin.service.AdminOperationMetricsService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/operation-metrics")
@RequiredArgsConstructor
public class AdminOperationMetricsController {

    private final AdminOperationMetricsService adminOperationMetricsService;

    @GetMapping
    public ApiResponse<AdminOperationMetricsDTO> metrics() {
        return ApiResponse.success(adminOperationMetricsService.collect());
    }
}
