package org.example.all_my_trip_project.domain.ticket;

import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketCancelResponse;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.ticket.service.TicketService;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    private TicketDAO ticketDAO;
    private TripDAO tripDAO;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketDAO = mock(TicketDAO.class);
        tripDAO = mock(TripDAO.class);
        service = new TicketService(ticketDAO, tripDAO);
    }

    @Test
    @DisplayName("상품 스냅샷과 수량 총액으로 여행의 모의 예약을 만든다")
    void reservesTicket() {
        stubOwnedTrip();
        when(ticketDAO.findByRequestKey(7L, "request-1")).thenReturn(Optional.empty());
        when(ticketDAO.findSlotForUpdate(31L)).thenReturn(Optional.of(offer(5)));
        when(ticketDAO.reserveInventory(31L, 2)).thenReturn(1);

        TicketReservationDTO result = service.reserve(7L,
                new CreateTicketReservationRequest(10L, 31L, 2, "request-1"));

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("40000");
        assertThat(result.getProductName()).isEqualTo("제주 아쿠아리움 입장권");
        verify(ticketDAO).insertReservation(result);
        verify(ticketDAO).insertReservationItem(result);
    }

    @Test
    @DisplayName("같은 요청 키를 다시 보내면 재고를 두 번 차감하지 않는다")
    void returnsExistingReservationForSameRequestKey() {
        stubOwnedTrip();
        TicketReservationDTO existing = TicketReservationDTO.builder()
                .reservationId(99L).tripId(10L).userId(7L).requestKey("same").build();
        when(ticketDAO.findByRequestKey(7L, "same")).thenReturn(Optional.of(existing));

        assertThat(service.reserve(7L,
                new CreateTicketReservationRequest(10L, 31L, 1, "same"))).isSameAs(existing);
        verify(ticketDAO, never()).reserveInventory(any(), any(Integer.class));
        verify(ticketDAO, never()).insertReservation(any());
    }

    @Test
    @DisplayName("남은 재고보다 많이 요청하면 예약을 만들지 않는다")
    void rejectsQuantityOverInventory() {
        stubOwnedTrip();
        when(ticketDAO.findByRequestKey(7L, "too-many")).thenReturn(Optional.empty());
        when(ticketDAO.findSlotForUpdate(31L)).thenReturn(Optional.of(offer(1)));

        assertThatThrownBy(() -> service.reserve(7L,
                new CreateTicketReservationRequest(10L, 31L, 2, "too-many")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TICKET_NOT_AVAILABLE));
        verify(ticketDAO, never()).insertReservation(any());
    }

    @Test
    @DisplayName("티켓 사용일이 여행 밖이면 예약하지 않는다")
    void rejectsUsageDateOutsideTrip() {
        stubOwnedTrip();
        when(ticketDAO.findByRequestKey(7L, "outside")).thenReturn(Optional.empty());
        TicketOfferDTO outside = offer(5);
        outside.setUsageDate(LocalDate.of(2026, 8, 25));
        when(ticketDAO.findSlotForUpdate(31L)).thenReturn(Optional.of(outside));

        assertThatThrownBy(() -> service.reserve(7L,
                new CreateTicketReservationRequest(10L, 31L, 1, "outside")))
                /*
                 * 무엇이 잘못됐는지 말해 주는 코드여야 한다. 예전 문구는 사용일·수량·여행을
                 * 뭉뚱그려서, 손님이 수량을 줄여 보거나 여행을 다시 고르며 헤맸다.
                 */
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TICKET_DATE_OUTSIDE_TRIP));
    }

    @Test
    @DisplayName("모의 예약을 취소하면 상태를 바꾸고 잡아 둔 재고를 복구한다")
    void cancelsAndReleasesInventory() {
        stubOwnedTrip();
        TicketReservationDTO reservation = TicketReservationDTO.builder()
                .reservationId(90L).tripId(10L).userId(7L).status("PENDING")
                .slotId(31L).quantity(2).build();
        when(ticketDAO.findForCancel(7L, 90L)).thenReturn(Optional.of(reservation));
        when(ticketDAO.cancelReservation(90L)).thenReturn(1);
        when(ticketDAO.releaseInventory(31L, 2)).thenReturn(1);

        TicketCancelResponse result = service.cancel(7L, 90L);

        assertThat(result.reservation().getStatus()).isEqualTo("CANCELLED");
        /* 결제 전이라 환불할 것이 없다. */
        assertThat(result.refunded()).isFalse();
        assertThat(result.cancelledTickets()).isZero();
        verify(ticketDAO).cancelReservation(90L);
        verify(ticketDAO).releaseInventory(31L, 2);
    }

    /* ── 결제한 예약 취소(환불) ── */

    private TicketReservationDTO confirmed(LocalDate usageDate) {
        return TicketReservationDTO.builder()
                .reservationId(90L).tripId(10L).userId(7L).status("CONFIRMED")
                .slotId(31L).quantity(2).usageDate(usageDate).build();
    }

    /*
     * 결제한 예약을 취소하면 넷이 함께 움직여야 한다. 하나라도 빠지면 어긋난다 —
     * 티켓을 무효로 만들지 않으면 환불받고도 입장할 수 있고, 재고를 반납하지 않으면
     * 판 적 없는 자리가 잠긴다.
     */
    @Test
    @DisplayName("결제한 예약을 취소하면 환불·티켓 무효·재고 반납이 함께 일어난다")
    void refundsConfirmedReservation() {
        stubOwnedTrip();
        when(ticketDAO.findForCancel(7L, 90L))
                .thenReturn(Optional.of(confirmed(LocalDate.now().plusDays(2))));
        when(ticketDAO.lockIssuedTicketStatuses(90L)).thenReturn(List.of("ISSUED", "ISSUED"));
        when(ticketDAO.cancelConfirmedReservation(90L)).thenReturn(1);
        when(ticketDAO.cancelIssuedTickets(90L)).thenReturn(2);
        when(ticketDAO.releaseInventory(31L, 2)).thenReturn(1);

        TicketCancelResponse result = service.cancel(7L, 90L);

        assertThat(result.refunded()).isTrue();
        assertThat(result.cancelledTickets()).isEqualTo(2);
        assertThat(result.reservation().getStatus()).isEqualTo("CANCELLED");
        verify(ticketDAO).cancelIssuedTickets(90L);
        verify(ticketDAO).refundPayments(90L);
        verify(ticketDAO).releaseInventory(31L, 2);
    }

    /*
     * 입장한 뒤에 환불받는 것은 막아야 한다. 2매 중 1매만 썼어도 거부한다 —
     * 부분 환불이 없어 쓴 만큼만 빼고 돌려줄 방법이 없다.
     */
    @Test
    @DisplayName("한 장이라도 사용했으면 취소할 수 없다")
    void rejectsRefundWhenAnyTicketUsed() {
        stubOwnedTrip();
        when(ticketDAO.findForCancel(7L, 90L))
                .thenReturn(Optional.of(confirmed(LocalDate.now().plusDays(2))));
        when(ticketDAO.lockIssuedTicketStatuses(90L)).thenReturn(List.of("ISSUED", "USED"));

        assertThatThrownBy(() -> service.cancel(7L, 90L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TICKET_ALREADY_USED);

        verify(ticketDAO, never()).refundPayments(any());
        verify(ticketDAO, never()).releaseInventory(any(), anyInt());
    }

    @Test
    @DisplayName("이용일이 지나면 취소할 수 없다")
    void rejectsRefundAfterUsageDate() {
        stubOwnedTrip();
        when(ticketDAO.findForCancel(7L, 90L))
                .thenReturn(Optional.of(confirmed(LocalDate.now().minusDays(1))));

        assertThatThrownBy(() -> service.cancel(7L, 90L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TICKET_USAGE_DATE_PASSED);

        /* 여기서 걸리면 티켓을 잠그러 갈 필요도 없다. */
        verify(ticketDAO, never()).lockIssuedTicketStatuses(any());
    }

    /* 아침에 마음이 바뀌는 것까지 막을 이유는 없다. */
    @Test
    @DisplayName("이용일 당일에는 취소할 수 있다")
    void allowsRefundOnUsageDate() {
        stubOwnedTrip();
        when(ticketDAO.findForCancel(7L, 90L))
                .thenReturn(Optional.of(confirmed(LocalDate.now())));
        when(ticketDAO.lockIssuedTicketStatuses(90L)).thenReturn(List.of("ISSUED"));
        when(ticketDAO.cancelConfirmedReservation(90L)).thenReturn(1);
        when(ticketDAO.cancelIssuedTickets(90L)).thenReturn(1);
        when(ticketDAO.releaseInventory(31L, 2)).thenReturn(1);

        assertThat(service.cancel(7L, 90L).refunded()).isTrue();
    }

    /*
     * 예약만 취소되고 자리는 잠긴 채 남으면 아무도 그 자리를 살 수 없다.
     * 조용히 넘기지 않고 트랜잭션을 되돌린다.
     */
    @Test
    @DisplayName("재고를 되돌리지 못하면 취소 전체를 되돌린다")
    void failsWhenInventoryCannotBeReleased() {
        stubOwnedTrip();
        when(ticketDAO.findForCancel(7L, 90L))
                .thenReturn(Optional.of(confirmed(LocalDate.now().plusDays(2))));
        when(ticketDAO.lockIssuedTicketStatuses(90L)).thenReturn(List.of("ISSUED"));
        when(ticketDAO.cancelConfirmedReservation(90L)).thenReturn(1);
        when(ticketDAO.cancelIssuedTickets(90L)).thenReturn(1);
        when(ticketDAO.releaseInventory(31L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(7L, 90L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고를 되돌리지 못했습니다");
    }

    @Test
    @DisplayName("이미 취소된 예약은 그대로 돌려준다")
    void returnsAlreadyCancelledAsIs() {
        stubOwnedTrip();
        when(ticketDAO.findForCancel(7L, 90L)).thenReturn(Optional.of(
                TicketReservationDTO.builder()
                        .reservationId(90L).tripId(10L).userId(7L).status("CANCELLED")
                        .slotId(31L).quantity(2).build()));

        TicketCancelResponse result = service.cancel(7L, 90L);

        assertThat(result.refunded()).isFalse();
        verify(ticketDAO, never()).refundPayments(any());
        verify(ticketDAO, never()).releaseInventory(any(), anyInt());
    }

    @Test
    @DisplayName("이미 사용 완료된 예약은 취소할 수 없다")
    void rejectsCancellingUsedReservation() {
        stubOwnedTrip();
        when(ticketDAO.findForCancel(7L, 90L)).thenReturn(Optional.of(
                TicketReservationDTO.builder()
                        .reservationId(90L).tripId(10L).userId(7L).status("USED")
                        .slotId(31L).quantity(2).build()));

        assertThatThrownBy(() -> service.cancel(7L, 90L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TICKET_CANCEL_NOT_ALLOWED);
    }

    private void stubOwnedTrip() {
        when(tripDAO.findById(10L)).thenReturn(Optional.of(TripDTO.builder()
                .tripId(10L).userId(7L)
                .startDate(LocalDate.of(2026, 8, 17)).endDate(LocalDate.of(2026, 8, 19))
                .build()));
    }

    private TicketOfferDTO offer(int remaining) {
        return TicketOfferDTO.builder()
                .productId(1L).productName("제주 아쿠아리움 입장권").placeName("아쿠아플라넷 제주")
                .optionId(11L).optionName("성인 입장권").slotId(31L)
                .usageDate(LocalDate.of(2026, 8, 18)).startTime(LocalTime.of(10, 0))
                .unitPrice(new BigDecimal("20000")).currency("KRW")
                .maxQuantityPerUser(4).remainingQuantity(remaining).build();
    }
}
