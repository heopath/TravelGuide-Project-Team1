package org.example.all_my_trip_project.domain.payment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.payment.dto.KakaoPayReadyResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.payment.service.KakaoPayService;
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
 * 카카오페이 결제. (#281)
 *
 * <p>결제를 시작하는 것과 승인하는 것이 나뉜다. 사이에 손님이 카카오 화면으로 다녀오기
 * 때문이다. 거래번호는 그동안 서버가 들고 있는다 — 화면에 내려 줬다가 되돌려받으면
 * 다른 결제의 거래번호를 끼워 넣어 승인시키는 길이 열린다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/payments/kakao")
@RequiredArgsConstructor
public class KakaoPayController {

    private final KakaoPayService kakaoPayService;

    /** 결제 시작. 금액과 상품명은 예약에서 읽으므로 예약 번호만 받는다. */
    @PostMapping("/ready")
    public ApiResponse<KakaoPayReadyResponse> ready(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ReadyRequest request) {
        return ApiResponse.success("카카오페이 결제를 시작합니다.",
                kakaoPayService.ready(requireUserId(principal), request.reservationId()));
    }

    /** 결제 승인. 어느 예약인지는 결제를 시작할 때 서버가 적어 둔 기록에서 꺼낸다. */
    @PostMapping("/approve")
    public ApiResponse<PaymentResultResponse> approve(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ApproveRequest request) {
        PaymentResultResponse result =
                kakaoPayService.approve(requireUserId(principal), request.pgToken());
        return ApiResponse.success(
                result.replayed() ? "이미 결제된 예약입니다." : "결제가 완료되었습니다.",
                result);
    }

    /**
     * 손님이 카카오 화면에서 되돌아 나온 경우.
     *
     * <p>남은 기록을 지운다. 그냥 두면 다음 결제가 헌 거래번호를 물고 시작한다.
     */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal AuthenticatedUser principal) {
        kakaoPayService.cancel(requireUserId(principal));
        return ApiResponse.success("결제를 취소했습니다.", null);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }

    public record ReadyRequest(@NotNull @Positive Long reservationId) {}

    public record ApproveRequest(@NotBlank @Size(max = 200) String pgToken) {}
}
