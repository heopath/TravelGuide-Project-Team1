package org.example.all_my_trip_project.domain.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanSaveRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanSaveResult;
import org.example.all_my_trip_project.domain.ai.service.AiTripPlanPersistenceService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/ai-trip-plans")
@RequiredArgsConstructor
public class AiTripPlanSaveController {
    private final AiTripPlanPersistenceService persistenceService;

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<AiTripPlanSaveResult>> save(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AiTripPlanSaveRequest request
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AiTripPlanSaveResult result = persistenceService.save(principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + result.tripId()))
                .body(ApiResponse.success("AI 여행 일정이 내 여행에 저장되었습니다.", result));
    }
}
