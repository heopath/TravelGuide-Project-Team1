package org.example.all_my_trip_project.domain.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanResponse;
import org.example.all_my_trip_project.domain.ai.service.AiTripPlanService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-trip-plans")
@RequiredArgsConstructor
public class AiTripPlanController {
    private final AiTripPlanService aiTripPlanService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiTripPlanResponse>> generate(
            @Valid @RequestBody AiTripPlanRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI 여행 일정 초안을 생성했습니다.",
                aiTripPlanService.generate(request)
        ));
    }
}
