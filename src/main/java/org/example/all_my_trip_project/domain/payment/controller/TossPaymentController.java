package org.example.all_my_trip_project.domain.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.payment.dto.TossConfirmRequest;
import org.example.all_my_trip_project.domain.payment.service.TossPaymentService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스 결제창에서 돌아온 결제를 승인한다.
 *
 * <p>결제창을 띄우는 것은 화면이 하지만 <b>승인은 서버만</b> 한다. 시크릿 키가 필요하고,
 * 그 키가 브라우저에 있으면 누구나 우리 이름으로 승인을 부를 수 있다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/payments/toss")
@RequiredArgsConstructor
public class TossPaymentController {

    private final TossPaymentService tossPaymentService;

    @PostMapping("/confirm")
    public ApiResponse<PaymentResultResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TossConfirmRequest request) {
        PaymentResultResponse result =
                tossPaymentService.confirm(requireUserId(principal), request);
        return ApiResponse.success(
                result.replayed() ? "이미 결제된 예약입니다." : "결제가 완료되었습니다.",
                result);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
