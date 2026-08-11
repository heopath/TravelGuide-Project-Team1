package org.example.all_my_trip_project.domain.ticket;

import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TICKET_REQUEST));
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

        TicketReservationDTO result = service.cancel(7L, 90L);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(ticketDAO).cancelReservation(90L);
        verify(ticketDAO).releaseInventory(31L, 2);
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
