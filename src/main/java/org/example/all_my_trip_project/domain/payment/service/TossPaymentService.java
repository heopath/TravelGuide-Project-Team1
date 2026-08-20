package org.example.all_my_trip_project.domain.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentRequest;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.payment.dto.TossConfirmRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 결제 승인.
 *
 * <p><b>여기서만 실제 결제사를 부른다.</b> 다른 결제수단은 전부 모의라 바깥으로 나가지 않는다.
 * 테스트 키를 쓰면 실제 돈이 오가지 않지만, 승인 흐름과 응답 모양은 진짜다.
 *
 * <pre>
 *   화면            토스 결제창                 우리 서버
 *   ────            ──────────                 ────────
 *   위젯 띄우기  →   카드 정보 입력·인증
 *                   성공하면 successUrl로 복귀
 *   승인 요청    →                         →   confirm(paymentKey…)
 *                                              토스에 승인 요청 → 우리 결제 기록
 * </pre>
 *
 * <p>승인은 <b>서버에서만</b> 한다. 시크릿 키가 필요하고, 그 키가 브라우저에 있으면 누구나
 * 우리 이름으로 승인을 부를 수 있다.
 */
@Slf4j
@Service
@Profile("!ui")
public class TossPaymentService {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    /** 결제 기록에 남을 결제사. 모의 결제({@code MOCK})와 갈리는 값이다. */
    private static final String ACQUIRER = "TOSS";

    /**
     * 토스가 돌려준 결제수단을 우리 값으로 옮긴다.
     *
     * <p>모르는 수단은 카드로 두지 않고 간편결제로 본다 — 토스가 새 수단을 추가했을 때
     * 카드로 기록해 버리면 정산 기준이 틀어진다.
     */
    private static final Map<String, String> METHODS = Map.of(
            "카드", "CARD",
            "가상계좌", "VIRTUAL_ACCOUNT",
            "계좌이체", "TRANSFER",
            "간편결제", "EASY_PAY"
    );

    private final PaymentService paymentService;
    private final PaymentDAO paymentDAO;
    private final RestClient restClient;
    private final String secretKey;

    public TossPaymentService(PaymentService paymentService,
                              PaymentDAO paymentDAO,
                              RestClient.Builder restClientBuilder,
                              @Value("${payment.toss.secret-key:}") String secretKey) {
        this.paymentService = paymentService;
        this.paymentDAO = paymentDAO;
        this.restClient = restClientBuilder.build();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
    }

    /** 시크릿 키가 없으면 승인을 부를 수 없다. 화면이 미리 알고 모의 결제로 돌아가게 한다. */
    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    /**
     * 결제를 승인하고 우리 결제로 기록한다.
     *
     * <p><b>어느 예약을 결제하는지는 화면 말을 믿지 않는다.</b> 주문번호에서 꺼낸다 —
     * 주문번호는 결제창을 띄울 때 토스에 함께 넘어가 그 결제에 묶인다. 화면이 보낸 예약을
     * 그대로 쓰면, 싼 예약으로 결제창을 띄우고 승인만 비싼 예약에 붙이는 일이 가능하다.
     *
     * <p>금액도 승인 전에 대조한다. 토스도 결제창을 띄울 때 받은 금액과 다르면 거절하지만,
     * 우리 예약 금액과 같은지는 우리만 안다.
     *
     * <p>멱등키로 토스가 준 {@code paymentKey}를 쓴다. 결제 하나에 하나뿐인 값이라 돌아오는
     * 주소를 두 번 열거나 새로고침해도 결제가 두 번 만들어지지 않는다.
     */
    @Transactional
    public PaymentResultResponse confirm(Long userId, TossConfirmRequest request) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.TOSS_NOT_CONFIGURED);
        }

        Long reservationId = reservationIdOf(request.orderId());
        if (reservationId == null) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }

        TicketReservationDTO reservation = paymentDAO.findReservation(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND));
        if (!userId.equals(reservation.getUserId())) {
            /* 남의 예약이라는 사실조차 알리지 않는다. 없는 것과 같게 답한다. */
            throw new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND);
        }
        if (!sameAmount(reservation.getTotalAmount(), request.amount())) {
            log.warn("토스 승인 금액이 예약 금액과 다릅니다: reservation={} expected={} paid={}",
                    reservationId, reservation.getTotalAmount(), request.amount());
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }

        Map<String, Object> approved = callConfirm(request);
        String method = METHODS.getOrDefault(String.valueOf(approved.get("method")), "EASY_PAY");

        /*
         * 간편결제로 온 경우에만 사업자를 남긴다. 토스를 거친 결제이므로 TOSS_PAY다 —
         * 응답의 easyPay.provider는 `토스페이`처럼 한글이라 우리 코드 자리에 그대로 넣을 수 없다.
         */
        String easyPayProvider = "EASY_PAY".equals(method) ? "TOSS_PAY" : null;

        /*
         * 결제사와 결제사 키를 함께 남긴다. 모의 결제와 섞이면 정산도 문의도 갈 곳이 없다.
         * 멱등키로도 paymentKey를 쓴다 — 결제 하나에 하나뿐인 값이라 돌아오는 주소를 두 번
         * 열어도 결제가 두 번 만들어지지 않는다.
         */
        return paymentService.pay(userId, reservationId,
                new PaymentRequest(method, request.paymentKey(), easyPayProvider),
                ACQUIRER, request.paymentKey());
    }

    private Map<String, Object> callConfirm(TossConfirmRequest request) {
        String basic = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                    .uri(CONFIRM_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "paymentKey", request.paymentKey(),
                            "orderId", request.orderId(),
                            "amount", request.amount()))
                    .retrieve()
                    .body(Map.class);

            if (body == null) throw new BusinessException(ErrorCode.TOSS_CONFIRM_FAILED);
            return body;
        } catch (RestClientResponseException exception) {
            /*
             * 토스가 준 이유를 로그에 남긴다. 금액 불일치나 이미 승인된 결제가 대부분인데,
             * 응답 본문 없이는 어느 쪽인지 알 수 없다. 손님에게는 이유를 그대로 전하지 않는다.
             */
            log.warn("토스 결제 승인 실패: status={} body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.TOSS_CONFIRM_FAILED);
        }
    }

    /** 화면이 만든 주문번호에서 예약 번호를 꺼낸다. `AMT-{예약번호}-{난수}` 모양이다. */
    public static Long reservationIdOf(String orderId) {
        String[] parts = String.valueOf(orderId).split("-");
        if (parts.length < 3 || !"AMT".equals(parts[0])) return null;
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 승인 금액이 예약 금액과 같은지. 다르면 주문서가 바뀐 것이다. */
    public static boolean sameAmount(BigDecimal expected, Long paid) {
        return expected != null && paid != null
                && expected.compareTo(BigDecimal.valueOf(paid)) == 0;
    }
}
