package org.example.all_my_trip_project.domain.payment.service;

import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentQrIssueResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentQrSummaryResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QR 결제. (#281)
 *
 * <p>여기서 보는 것은 "QR 하나로 결제 권한을 옮기는 일"이 안전한지다. 결제 자체는
 * {@link PaymentService}가 하고 그쪽 시험이 따로 본다.
 */
class PaymentQrServiceTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long RESERVATION_ID = 42L;

    private PaymentDAO paymentDAO;
    private PaymentService paymentService;
    private PaymentQrSigner signer;
    private PaymentQrService service;

    @BeforeEach
    void setUp() {
        paymentDAO = mock(PaymentDAO.class);
        paymentService = mock(PaymentService.class);
        /* 열쇠를 고정한다. 기동마다 바뀌면 시험이 만든 토큰과 서비스가 쓰는 열쇠가 갈린다. */
        signer = new PaymentQrSigner("test-secret");
        service = new PaymentQrService(paymentDAO, paymentService, signer);
    }

    private TicketReservationDTO reservation(String status, OffsetDateTime expiresAt) {
        return TicketReservationDTO.builder()
                .reservationId(RESERVATION_ID)
                .reservationNumber("AMT-TKT-ABC")
                .userId(USER_ID)
                .status(status)
                .productName("제주 아쿠아리움 입장권")
                .optionName("성인")
                .quantity(2)
                .totalAmount(new BigDecimal("40000"))
                .currency("KRW")
                .expiresAt(expiresAt)
                .build();
    }

    private void reservationIs(String status, OffsetDateTime expiresAt) {
        when(paymentDAO.findReservation(RESERVATION_ID))
                .thenReturn(Optional.of(reservation(status, expiresAt)));
    }

    /* ── 발급 ── */

    @Test
    @DisplayName("결제 전 예약에는 QR을 띄운다")
    void issuesQr() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));

        PaymentQrIssueResponse issued = service.issue(USER_ID, RESERVATION_ID);

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(issued.serverTime());
        /* 남은 시간을 손님 시계로 세지 않으려면 서버 시각이 함께 내려와야 한다. */
        assertThat(issued.serverTime()).isNotNull();
    }

    /*
     * 자리가 반납된 뒤에도 살아 있는 QR을 보여주면, 스캔해서 승인한 손님이 그 자리에서
     * 거절당한다. 예약이 먼저 끝나면 QR도 거기서 끝나야 한다.
     */
    @Test
    @DisplayName("예약이 QR보다 먼저 만료되면 QR도 그때 끝난다")
    void neverOutlivesReservation() {
        OffsetDateTime reservationExpiry = OffsetDateTime.now().plusMinutes(2);
        reservationIs("PENDING", reservationExpiry);

        PaymentQrIssueResponse issued = service.issue(USER_ID, RESERVATION_ID);

        assertThat(issued.expiresAt()).isEqualTo(reservationExpiry);
    }

    @Test
    @DisplayName("이미 결제된 예약에는 QR을 띄우지 않는다")
    void refusesQrForPaidReservation() {
        reservationIs("CONFIRMED", null);

        assertThatThrownBy(() -> service.issue(USER_ID, RESERVATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESERVATION_NOT_PAYABLE);
    }

    @Test
    @DisplayName("남의 예약에는 QR을 띄우지 않는다")
    void refusesQrForOtherUser() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));

        assertThatThrownBy(() -> service.issue(OTHER_USER_ID, RESERVATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_RESERVATION_NOT_FOUND);
    }

    /* ── 승인 ── */

    @Test
    @DisplayName("승인하면 QR 간편결제로 결제된다")
    void approvesAsQrEasyPay() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));
        String token = service.issue(USER_ID, RESERVATION_ID).token();

        service.approve(USER_ID, token);

        ArgumentCaptor<PaymentRequest> request = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentService).pay(eq(USER_ID), eq(RESERVATION_ID), request.capture());
        assertThat(request.getValue().method()).isEqualTo("EASY_PAY");
        /* 카드·카카오페이와 기록에서 갈려야 나중에 어떤 흐름으로 결제됐는지 알 수 있다. */
        assertThat(request.getValue().easyPayProvider()).isEqualTo("QR_PAY");
    }

    /*
     * 승인 버튼을 두 번 누르거나 응답이 유실돼 다시 보내는 경우다. 멱등키를 화면이 만들면
     * 폰과 PC가 서로 다른 키를 만들어 같은 QR로 두 번 결제될 수 있다.
     */
    @Test
    @DisplayName("같은 QR을 두 번 승인해도 멱등키가 같다")
    void reusesIdempotencyKeyForSameQr() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));
        String token = service.issue(USER_ID, RESERVATION_ID).token();

        service.approve(USER_ID, token);
        service.approve(USER_ID, token);

        ArgumentCaptor<PaymentRequest> request = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentService, times(2)).pay(eq(USER_ID), eq(RESERVATION_ID), request.capture());
        assertThat(request.getAllValues().get(0).idempotencyKey())
                .isEqualTo(request.getAllValues().get(1).idempotencyKey());
        assertThat(request.getAllValues().get(0).idempotencyKey()).startsWith("qr-");
    }

    @Test
    @DisplayName("만료된 QR로는 결제하지 않는다")
    void refusesExpiredQr() {
        String expired = signer.sign(RESERVATION_ID, USER_ID, OffsetDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> service.approve(USER_ID, expired))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_QR_EXPIRED);
        verify(paymentService, never()).pay(any(), any(), any());
    }

    /*
     * QR 사진은 캡처로 퍼질 수 있다. 남이 주워서 승인하면 그 사람 돈이 나가고, 우리 쪽에는
     * 남의 티켓을 남이 결제한 기록이 남는다.
     */
    @Test
    @DisplayName("QR을 만든 사람만 승인할 수 있다")
    void refusesApprovalByAnotherUser() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));
        String token = service.issue(USER_ID, RESERVATION_ID).token();

        assertThatThrownBy(() -> service.approve(OTHER_USER_ID, token))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_QR_INVALID);
        verify(paymentService, never()).pay(any(), any(), any());
    }

    /* 서명을 검사하지 않으면 예약 번호만 바꿔 남의 예약을 결제 대상으로 만들 수 있다. */
    @Test
    @DisplayName("고쳐 만든 QR은 통하지 않는다")
    void refusesForgedToken() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));
        String token = service.issue(USER_ID, RESERVATION_ID).token();
        String forged = token.substring(0, token.indexOf('.')) + ".AAAAAAAAAAAAAAAA";

        assertThatThrownBy(() -> service.approve(USER_ID, forged))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_QR_INVALID);
        verify(paymentService, never()).pay(any(), any(), any());
    }

    @Test
    @DisplayName("다른 열쇠로 만든 QR은 통하지 않는다")
    void refusesTokenFromAnotherKey() {
        String elsewhere = new PaymentQrSigner("another-secret")
                .sign(RESERVATION_ID, USER_ID, OffsetDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> service.approve(USER_ID, elsewhere))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_QR_INVALID);
    }

    /* ── 승인 전 확인 ── */

    @Test
    @DisplayName("스캔한 기기에 결제 내용을 보여준다")
    void showsSummaryBeforeApproving() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));
        String token = service.issue(USER_ID, RESERVATION_ID).token();

        PaymentQrSummaryResponse summary = service.summary(USER_ID, token);

        assertThat(summary.productName()).isEqualTo("제주 아쿠아리움 입장권");
        assertThat(summary.amount()).isEqualByComparingTo("40000");
        assertThat(summary.quantity()).isEqualTo(2);
        assertThat(summary.alreadyPaid()).isFalse();
    }

    /* 스캔이 늦어 이미 결제가 끝난 경우다. 승인 버튼을 그대로 두면 눌러 봐야 거절당한다. */
    @Test
    @DisplayName("이미 결제가 끝났으면 그 사실을 알린다")
    void marksAlreadyPaid() {
        reservationIs("PENDING", OffsetDateTime.now().plusMinutes(15));
        String token = service.issue(USER_ID, RESERVATION_ID).token();
        reservationIs("CONFIRMED", null);

        assertThat(service.summary(USER_ID, token).alreadyPaid()).isTrue();
    }
}
