package org.example.all_my_trip_project.domain.payment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.payment.dto.PaymentQrSummaryResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.payment.service.PaymentQrService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * QR을 스캔한 기기가 부르는 곳. (#281)
 *
 * <p>예약 번호가 주소에 없다. 스캔한 쪽은 토큰만 들고 오고, 어떤 예약인지는 토큰에서
 * 나온다. 주소로 예약을 받으면 남의 번호를 넣어 결제 내용을 들여다볼 수 있다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/payments/qr")
@RequiredArgsConstructor
public class PaymentQrController {

    private final PaymentQrService paymentQrService;

    /** 승인 요청. 토큰은 길어서 주소가 아니라 본문으로 받는다. */
    public record ApproveRequest(
            @NotBlank @Size(max = 200) String token
    ) {}

    /** 승인 전에 보여줄 결제 내용. 무엇을 얼마에 결제하는지 확인하고 누르게 한다. */
    @GetMapping
    public ApiResponse<PaymentQrSummaryResponse> summary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("token") String token) {
        return ApiResponse.success(paymentQrService.summary(requireUserId(principal), token));
    }

    /**
     * 승인. 여기가 실제 결제다. 성공하면 예약이 확정되고 티켓이 발급된다.
     *
     * <p>같은 QR로 두 번 들어와도 결제는 한 번만 된다. 멱등키를 토큰에서 만들기 때문이다.
     * 두 번째 응답은 {@code replayed}가 참이다.
     */
    @PostMapping("/approve")
    public ApiResponse<PaymentResultResponse> approve(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ApproveRequest request) {
        PaymentResultResponse result =
                paymentQrService.approve(requireUserId(principal), request.token());
        return ApiResponse.success(
                result.replayed() ? "이미 결제된 예약입니다." : "결제가 완료되었습니다.",
                result);
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
