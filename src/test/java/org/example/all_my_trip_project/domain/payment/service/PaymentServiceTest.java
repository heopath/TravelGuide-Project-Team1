package org.example.all_my_trip_project.domain.payment.service;

import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.IssuedTicketDTO;
import org.example.all_my_trip_project.domain.payment.dto.PayableReservationDTO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentDTO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentRequest;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final long USER_ID = 7L;
    private static final long RESERVATION_ID = 42L;
    private static final String KEY = "idem-1";

    private PaymentDAO paymentDAO;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentDAO = mock(PaymentDAO.class);
        service = new PaymentService(paymentDAO,
                mock(org.example.all_my_trip_project.domain.notification.service.NotificationService.class));
    }

    private PaymentRequest request() {
        return new PaymentRequest("CARD", KEY, null);
    }

    private PayableReservationDTO payable(String status, OffsetDateTime expiresAt, int quantity) {
        return PayableReservationDTO.builder()
                .reservationId(RESERVATION_ID)
                .reservationItemId(100L)
                .status(status)
                .totalAmount(new BigDecimal("30000"))
                .currencyCode("KRW")
                .expiresAt(expiresAt)
                .usageDate(LocalDate.now().plusDays(3))
                .usageStartTime(LocalTime.of(10, 0))
                .quantity(quantity)
                .build();
    }

    private void reservationExists() {
        when(paymentDAO.findReservation(RESERVATION_ID)).thenReturn(Optional.of(
                TicketReservationDTO.builder()
                        .reservationId(RESERVATION_ID).userId(USER_ID).status("CONFIRMED").build()));
    }

    @Test
    @DisplayName("결제하면 예약이 확정되고 수량만큼 티켓이 발급된다")
    void paysAndIssuesTickets() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("PENDING", OffsetDateTime.now().plusMinutes(10), 2)));
        when(paymentDAO.confirmReservation(RESERVATION_ID)).thenReturn(1);
        reservationExists();

        PaymentResultResponse result = service.pay(USER_ID, RESERVATION_ID, request());

        assertThat(result.replayed()).isFalse();
        assertThat(result.payment().getProvider()).isEqualTo("MOCK");
        assertThat(result.payment().getMethod()).isEqualTo("CARD");
        assertThat(result.tickets()).hasSize(2);
        verify(paymentDAO).confirmReservation(RESERVATION_ID);
        verify(paymentDAO, times(2)).insertIssuedTicket(any(), anyString());
    }

    /*
     * 검증 토큰은 DB에 해시로만 남고 원문은 응답에만 실린다. 이 둘이 뒤바뀌면 DB가 새는 순간
     * 그대로 입장 코드가 된다.
     */
    @Test
    @DisplayName("검증 토큰은 해시로 저장하고 원문은 응답에만 담는다")
    void storesOnlyTokenHash() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("PENDING", OffsetDateTime.now().plusMinutes(10), 1)));
        when(paymentDAO.confirmReservation(RESERVATION_ID)).thenReturn(1);
        reservationExists();

        PaymentResultResponse result = service.pay(USER_ID, RESERVATION_ID, request());

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(paymentDAO).insertIssuedTicket(any(), hash.capture());

        String token = result.tickets().get(0).getVerificationToken();
        assertThat(token).isNotBlank();
        /* CHAR(64) 컬럼과 길이가 맞아야 한다. SHA-256 16진수는 64자다. */
        assertThat(hash.getValue()).hasSize(64).isNotEqualTo(token);
    }

    @Test
    @DisplayName("티켓마다 번호와 토큰이 서로 다르다")
    void issuesDistinctTickets() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("PENDING", OffsetDateTime.now().plusMinutes(10), 3)));
        when(paymentDAO.confirmReservation(RESERVATION_ID)).thenReturn(1);
        reservationExists();

        List<IssuedTicketDTO> tickets = service.pay(USER_ID, RESERVATION_ID, request()).tickets();

        assertThat(tickets).extracting(IssuedTicketDTO::getTicketNumber).doesNotHaveDuplicates();
        assertThat(tickets).extracting(IssuedTicketDTO::getVerificationToken).doesNotHaveDuplicates();
    }

    /* ── 결제수단 (#281) ── */

    /*
     * 간편결제는 카카오페이든 토스든 method가 EASY_PAY로 같다. 사업자가 provider에 남지
     * 않으면 어디로 결제됐는지 기록에서 사라지고, 환불·문의 때 갈 곳을 알 수 없다.
     */
    @Test
    @DisplayName("간편결제는 사업자를 provider에 남긴다")
    void keepsEasyPayProvider() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("PENDING", OffsetDateTime.now().plusMinutes(10), 1)));
        when(paymentDAO.confirmReservation(RESERVATION_ID)).thenReturn(1);
        reservationExists();

        PaymentResultResponse result = service.pay(USER_ID, RESERVATION_ID,
                new PaymentRequest("EASY_PAY", KEY, "KAKAO_PAY"));

        assertThat(result.payment().getMethod()).isEqualTo("EASY_PAY");
        /* MOCK_이 붙어야 나중에 진짜 카카오페이를 붙였을 때 기록으로 구분된다. */
        assertThat(result.payment().getProvider()).isEqualTo("MOCK_KAKAO_PAY");
    }

    @Test
    @DisplayName("간편결제인데 사업자가 없으면 결제하지 않는다")
    void rejectsEasyPayWithoutProvider() {
        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID,
                new PaymentRequest("EASY_PAY", KEY, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_REQUEST);
        verify(paymentDAO, never()).insertPayment(any());
    }

    /* 화면이 고른 수단과 남는 기록이 어긋나는 요청이다. 조용히 무시하면 아무도 모른다. */
    @Test
    @DisplayName("간편결제가 아닌데 사업자를 보내면 결제하지 않는다")
    void rejectsProviderOnOtherMethods() {
        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID,
                new PaymentRequest("CARD", KEY, "TOSS_PAY")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_REQUEST);
        verify(paymentDAO, never()).insertPayment(any());
    }

    /*
     * 잘못된 요청은 예약을 잠그기 전에 걸러야 한다. 잠근 뒤에 400을 내면 그동안 같은 예약을
     * 결제하려는 다른 요청이 이유 없이 기다린다.
     */
    @Test
    @DisplayName("잘못된 결제수단 요청은 예약을 잠그기 전에 거부한다")
    void rejectsBeforeLocking() {
        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID,
                new PaymentRequest("EASY_PAY", KEY, null)))
                .isInstanceOf(BusinessException.class);
        verify(paymentDAO, never()).lockPayableReservation(any(), any());
    }

    /* ── 멱등 ── */

    @Test
    @DisplayName("같은 멱등키로 다시 부르면 결제를 새로 만들지 않는다")
    void replaysPreviousPayment() {
        PaymentDTO previous = PaymentDTO.builder()
                .paymentId(1L).reservationId(RESERVATION_ID).idempotencyKey(KEY).status("PAID").build();
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(previous));
        reservationExists();
        when(paymentDAO.findTicketsByReservation(RESERVATION_ID)).thenReturn(List.of());

        PaymentResultResponse result = service.pay(USER_ID, RESERVATION_ID, request());

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment().getPaymentId()).isEqualTo(1L);
        verify(paymentDAO, never()).insertPayment(any());
        verify(paymentDAO, never()).confirmReservation(any());
        verify(paymentDAO, never()).insertIssuedTicket(any(), anyString());
    }

    /*
     * 두 요청이 같은 순간에 들어오면 앞 트랜잭션이 커밋되기 전이라 조회로는 못 잡는다.
     * UNIQUE 제약이 마지막에 걸러 주고, 그때도 실패가 아니라 "이미 결제됨"이어야 한다.
     */
    @Test
    @DisplayName("동시에 들어와 UNIQUE에 걸리면 앞의 결제를 돌려준다")
    void replaysWhenUniqueViolationRaces() {
        PaymentDTO previous = PaymentDTO.builder()
                .paymentId(1L).reservationId(RESERVATION_ID).idempotencyKey(KEY).status("PAID").build();
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(previous));
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("PENDING", OffsetDateTime.now().plusMinutes(10), 1)));
        when(paymentDAO.insertPayment(any())).thenThrow(new DuplicateKeyException("idempotency_key"));
        reservationExists();
        when(paymentDAO.findTicketsByReservation(RESERVATION_ID)).thenReturn(List.of());

        PaymentResultResponse result = service.pay(USER_ID, RESERVATION_ID, request());

        assertThat(result.replayed()).isTrue();
        verify(paymentDAO, never()).confirmReservation(any());
    }

    @Test
    @DisplayName("다른 예약에 쓰인 멱등키는 거부한다")
    void rejectsKeyReusedForAnotherReservation() {
        PaymentDTO other = PaymentDTO.builder()
                .paymentId(1L).reservationId(999L).idempotencyKey(KEY).status("PAID").build();
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAYMENT_REQUEST);
    }

    /* ── 거부 ── */

    @Test
    @DisplayName("만료 시각이 지난 예약은 결제할 수 없다")
    void rejectsExpiredReservation() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("PENDING", OffsetDateTime.now().minusSeconds(1), 1)));

        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_EXPIRED);

        verify(paymentDAO, never()).insertPayment(any());
    }

    @Test
    @DisplayName("이미 EXPIRED로 정리된 예약은 결제할 수 없다")
    void rejectsAlreadyExpiredStatus() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("EXPIRED", OffsetDateTime.now().plusMinutes(10), 1)));

        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_EXPIRED);
    }

    @Test
    @DisplayName("이미 확정된 예약은 다시 결제할 수 없다")
    void rejectsConfirmedReservation() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("CONFIRMED", OffsetDateTime.now().plusMinutes(10), 1)));

        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_PAYABLE);
    }

    /*
     * 잠갔는데도 확정이 0건이면 그 사이 만료 정리가 가져간 것이다. 여기서 통과시키면
     * 돈만 받고 자리는 없는 상태가 된다.
     */
    @Test
    @DisplayName("확정 직전에 만료 정리가 가져갔으면 결제를 되돌린다")
    void rollsBackWhenConfirmLosesRace() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID))
                .thenReturn(Optional.of(payable("PENDING", OffsetDateTime.now().plusMinutes(10), 1)));
        when(paymentDAO.confirmReservation(RESERVATION_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_EXPIRED);

        verify(paymentDAO, never()).insertIssuedTicket(any(), anyString());
    }

    @Test
    @DisplayName("없는 예약은 찾을 수 없다고 알린다")
    void rejectsUnknownReservation() {
        when(paymentDAO.findByIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(paymentDAO.lockPayableReservation(USER_ID, RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(USER_ID, RESERVATION_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TICKET_RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 예약 티켓은 조회할 수 없다")
    void rejectsTicketsOfAnotherUser() {
        when(paymentDAO.findReservation(RESERVATION_ID)).thenReturn(Optional.of(
                TicketReservationDTO.builder()
                        .reservationId(RESERVATION_ID).userId(999L).status("CONFIRMED").build()));

        assertThatThrownBy(() -> service.tickets(USER_ID, RESERVATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TICKET_RESERVATION_NOT_FOUND);

        verify(paymentDAO, never()).findTicketsByReservation(eq(RESERVATION_ID));
    }
}
