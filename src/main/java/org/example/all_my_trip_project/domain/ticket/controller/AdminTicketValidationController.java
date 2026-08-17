package org.example.all_my_trip_project.domain.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationLogDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationResponse;
import org.example.all_my_trip_project.domain.ticket.service.TicketValidationService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/v1/admin/**}는 {@code ApiSecurityConfig}에서 이미 {@code ROLE_ADMIN}을 요구한다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/ticket-validations")
@RequiredArgsConstructor
public class AdminTicketValidationController {

    private final TicketValidationService ticketValidationService;

    /**
     * 검표. 실패도 200으로 돌려준다.
     *
     * <p>없는 코드나 이미 쓴 티켓은 요청이 잘못된 것이 아니라 검표가 답해야 할 상황이다.
     * 4xx로 돌려주면 화면이 오류 경로로 빠져 정작 중요한 "왜 안 되는지"를 보여주기 어렵다.
     */
    @PostMapping
    public ApiResponse<TicketValidationResponse> validate(
            @Valid @RequestBody TicketValidationRequest request) {
        TicketValidationResponse result = ticketValidationService.validate(request);
        return ApiResponse.success(result.message(), result);
    }

    @GetMapping
    public ApiResponse<List<TicketValidationLogDTO>> logs(
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.success(ticketValidationService.recentLogs(result, limit));
    }
}
