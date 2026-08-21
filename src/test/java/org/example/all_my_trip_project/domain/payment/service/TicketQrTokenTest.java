package org.example.all_my_trip_project.domain.payment.service;

import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.IssuedTicketDTO;
import org.example.all_my_trip_project.domain.payment.dto.TicketQrResponse;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QR 토큰 재발급. (#265)
 *
 * <p>발권 때 준 코드는 그대로 두고 대조용 토큰을 하나 더 만든다. 저장되는 것은 여전히
 * 해시뿐이라, 평문을 두지 않는 설계를 깨지 않는다.
 */
class TicketQrTokenTest {

    private static final long USER_ID = 34L;
    private static final long RESERVATION_ID = 35L;
    private static final long TICKET_ID = 1L;

    private PaymentDAO paymentDAO;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentDAO = mock(PaymentDAO.class);
        service = new PaymentService(paymentDAO,
                mock(org.example.all_my_trip_project.domain.notification.service.NotificationService.class));

        when(paymentDAO.findReservation(RESERVATION_ID)).thenReturn(Optional.of(
                TicketReservationDTO.builder().reservationId(RESERVATION_ID).userId(USER_ID).build()));
        when(paymentDAO.findIssuedTicket(RESERVATION_ID, TICKET_ID)).thenReturn(Optional.of(
                IssuedTicketDTO.builder().issuedTicketId(TICKET_ID)
                        .ticketNumber("AMT-TKN-000000000001").status("ISSUED").build()));
        when(paymentDAO.updateQrToken(anyLong(), anyString(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("토큰은 응답에만 실리고 저장되는 것은 해시다")
    void storesOnlyHash() {
        TicketQrResponse response = service.issueQrToken(USER_ID, RESERVATION_ID, TICKET_ID);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(paymentDAO).updateQrToken(eq(TICKET_ID), hash.capture(), any());

        assertThat(response.token()).isNotBlank();
        /* SHA-256 16진수는 64자다. 평문이 그대로 저장되면 길이부터 다르다. */
        assertThat(hash.getValue()).hasSize(64).isNotEqualTo(response.token());
        assertThat(response.ticketNumber()).isEqualTo("AMT-TKN-000000000001");
    }

    @Test
    @DisplayName("부를 때마다 다른 토큰을 준다")
    void issuesDifferentTokenEachTime() {
        String first = service.issueQrToken(USER_ID, RESERVATION_ID, TICKET_ID).token();
        String second = service.issueQrToken(USER_ID, RESERVATION_ID, TICKET_ID).token();

        /* 같으면 앞서 띄운 QR이 계속 통한다는 뜻이라 유효기간을 두는 의미가 없다. */
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("유효기간은 5분이고 서버 시각을 함께 준다")
    void expiresInFiveMinutes() {
        TicketQrResponse response = service.issueQrToken(USER_ID, RESERVATION_ID, TICKET_ID);

        assertThat(Duration.between(response.serverTime(), response.expiresAt()))
                .isEqualTo(Duration.ofMinutes(5));
        /* 남은 시간을 손님 시계로 계산하면 시계가 틀어진 기기에서 어긋난다. */
        assertThat(response.serverTime()).isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    @DisplayName("남의 예약이면 발급하지 않는다")
    void rejectsOtherUsersReservation() {
        assertThatThrownBy(() -> service.issueQrToken(USER_ID + 1, RESERVATION_ID, TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_RESERVATION_NOT_FOUND);
        verify(paymentDAO, never()).updateQrToken(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("취소·사용 완료된 티켓에는 발급하지 않는다")
    void rejectsUnusableTicket() {
        for (String status : new String[] {"CANCELLED", "USED", "EXPIRED"}) {
            when(paymentDAO.findIssuedTicket(RESERVATION_ID, TICKET_ID)).thenReturn(Optional.of(
                    IssuedTicketDTO.builder().issuedTicketId(TICKET_ID).status(status).build()));

            assertThatThrownBy(() -> service.issueQrToken(USER_ID, RESERVATION_ID, TICKET_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_QR_NOT_AVAILABLE);
        }
        verify(paymentDAO, never()).updateQrToken(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("읽고 쓰는 사이에 상태가 바뀌면 조용히 넘기지 않는다")
    void failsWhenUpdateAffectsNoRow() {
        /* UPDATE에 status='ISSUED' 조건이 있어 그 사이 취소되면 0건이 된다. */
        when(paymentDAO.updateQrToken(anyLong(), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.issueQrToken(USER_ID, RESERVATION_ID, TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_QR_NOT_AVAILABLE);
    }
}
