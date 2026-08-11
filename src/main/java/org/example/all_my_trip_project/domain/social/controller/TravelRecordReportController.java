package org.example.all_my_trip_project.domain.social.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.social.dto.ProcessReportRequest;
import org.example.all_my_trip_project.domain.social.dto.ReportRecordRequest;
import org.example.all_my_trip_project.domain.social.dto.TravelRecordReportResponse;
import org.example.all_my_trip_project.domain.social.service.TravelRecordReportService;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequiredArgsConstructor
public class TravelRecordReportController {

    private static final String ADMIN_ROLE = "ADMIN";

    private final TravelRecordReportService travelRecordReportService;

    @PostMapping("/api/v1/travel-records/{travelRecordId}/reports")
    public ResponseEntity<ApiResponse<TravelRecordReportResponse>> report(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long travelRecordId,
            @Valid @RequestBody ReportRecordRequest request) {
        TravelRecordReportResponse response =
                travelRecordReportService.report(requireUserId(principal), travelRecordId, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/travel-record-reports/" + response.travelRecordReportId()))
                .body(ApiResponse.success("신고가 접수되었습니다.", response));
    }

    @GetMapping("/api/v1/travel-record-reports")
    public ApiResponse<List<TravelRecordReportResponse>> getReports(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) ReportStatus status) {
        requireAdmin(principal);
        return ApiResponse.success(travelRecordReportService.getReports(principal.userId(), status));
    }

    @PatchMapping("/api/v1/travel-record-reports/{reportId}")
    public ApiResponse<TravelRecordReportResponse> process(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reportId,
            @Valid @RequestBody ProcessReportRequest request) {
        requireAdmin(principal);
        TravelRecordReportResponse response =
                travelRecordReportService.process(principal.userId(), reportId, request);
        return ApiResponse.success("신고 처리 결과가 저장되었습니다.", response);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }

    private void requireAdmin(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!ADMIN_ROLE.equals(principal.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
