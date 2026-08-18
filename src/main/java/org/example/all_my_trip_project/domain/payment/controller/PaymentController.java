package org.example.all_my_trip_project.domain.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.payment.dto.IssuedTicketDTO;
import org.example.all_my_trip_project.domain.payment.dto.TicketQrResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentRequest;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.payment.service.PaymentService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/ticket-reservations/{reservationId}")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 모의 결제. 성공하면 예약이 확정되고 티켓이 함께 발급된다.
     *
     * <p>같은 멱등키로 다시 부르면 결제를 새로 만들지 않고 앞의 결과를 돌려준다. 응답의
     * {@code replayed}로 구분할 수 있다.
     */
    @PostMapping("/payment")
    public ApiResponse<PaymentResultResponse> pay(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reservationId,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResultResponse result =
                paymentService.pay(requireUserId(principal), reservationId, request);
        return ApiResponse.success(
                result.replayed() ? "이미 결제된 예약입니다." : "결제가 완료되었습니다.",
                result);
    }

    @GetMapping("/tickets")
    public ApiResponse<List<IssuedTicketDTO>> tickets(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reservationId) {
        return ApiResponse.success(paymentService.tickets(requireUserId(principal), reservationId));
    }

    /**
     * QR에 담을 입장 코드를 새로 발급한다. (#265)
     *
     * <p>GET이 아니라 POST다. 부를 때마다 토큰을 새로 만들어 상태가 바뀐다. 앞서 띄운 QR은
     * 그 순간부터 통하지 않는다.
     *
     * <p>응답의 {@code token}은 여기서만 나온다. 서버는 해시만 저장하므로 다시 받아올 수 없다.
     */
    @PostMapping("/tickets/{issuedTicketId}/qr")
    public ApiResponse<TicketQrResponse> issueQr(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long reservationId,
            @PathVariable Long issuedTicketId) {
        return ApiResponse.success("입장 코드를 발급했습니다.",
                paymentService.issueQrToken(requireUserId(principal), reservationId, issuedTicketId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return principal.userId();
    }
}
