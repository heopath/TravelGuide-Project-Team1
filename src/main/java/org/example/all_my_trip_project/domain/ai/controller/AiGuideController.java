package org.example.all_my_trip_project.domain.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.service.AiGuideService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-guides")
@RequiredArgsConstructor
public class AiGuideController {
    private final AiGuideService aiGuideService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiGuideResponse>> generate(
            @Valid @RequestBody AiGuideRequest request,
            @RequestHeader(value = "X-AI-Mock-Mode", required = false) String mockMode
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI 여행 일정 추천이 완료되었습니다.",
                aiGuideService.generate(request, "server-error".equals(mockMode))
        ));
    }
}
