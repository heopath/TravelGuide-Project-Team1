package org.example.all_my_trip_project.domain.ticket.service;

import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 티켓 구매가 여행에서 분리됐는지 본다. (#255)
 *
 * <p>여행 없이 살 수 있어야 하고, 여행을 보냈을 때만 기간을 따져야 한다.
 */
class TicketTripDecouplingTest {

    private static final long USER_ID = 3L;
    private static final long SLOT_ID = 55L;
    private static final long TRIP_ID = 9L;

    private TicketDAO ticketDAO;
    private TripDAO tripDAO;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketDAO = mock(TicketDAO.class);
        tripDAO = mock(TripDAO.class);
        service = new TicketService(ticketDAO, tripDAO);

        when(ticketDAO.findByRequestKey(anyLong(), any())).thenReturn(Optional.empty());
        when(ticketDAO.findSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(offer()));
        when(ticketDAO.reserveInventory(eq(SLOT_ID), anyInt())).thenReturn(1);
    }

    private TicketOfferDTO offer() {
        return TicketOfferDTO.builder()
                .slotId(SLOT_ID)
                .productName("모의 관광 티켓")
                .optionName("일반 이용권")
                .usageDate(LocalDate.of(2026, 9, 15))
                .unitPrice(new BigDecimal("36000"))
                .currency("KRW")
                .maxQuantityPerUser(4)
                .remainingQuantity(100)
                .build();
    }

    private TripDTO trip(LocalDate start, LocalDate end) {
        return TripDTO.builder().tripId(TRIP_ID).userId(USER_ID).startDate(start).endDate(end).build();
    }

    private CreateTicketReservationRequest request(Long tripId) {
        return new CreateTicketReservationRequest(tripId, SLOT_ID, 2, "key-1");
    }

    @Test
    @DisplayName("여행 없이도 티켓을 살 수 있다")
    void reservesWithoutTrip() {
        TicketReservationDTO reservation = service.reserve(USER_ID, request(null));

        assertThat(reservation.getTripId()).isNull();
        assertThat(reservation.getQuantity()).isEqualTo(2);
        /* 여행을 안 보냈으면 여행을 찾아보지도 않아야 한다. */
        verify(tripDAO, never()).findById(anyLong());
        verify(ticketDAO).insertReservation(any());
    }

    @Test
    @DisplayName("여행을 보내면 이용일이 여행 기간 안이어야 한다")
    void rejectsUsageDateOutsideTrip() {
        when(tripDAO.findById(TRIP_ID)).thenReturn(Optional.of(
                trip(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 27))));

        assertThatThrownBy(() -> service.reserve(USER_ID, request(TRIP_ID)))
                .isInstanceOf(BusinessException.class);
        /* 거부됐으면 재고를 잡아서는 안 된다. */
        verify(ticketDAO, never()).reserveInventory(anyLong(), anyInt());
    }

    @Test
    @DisplayName("기간이 맞으면 여행에 붙여서 산다")
    void reservesWithMatchingTrip() {
        when(tripDAO.findById(TRIP_ID)).thenReturn(Optional.of(
                trip(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16))));

        assertThat(service.reserve(USER_ID, request(TRIP_ID)).getTripId()).isEqualTo(TRIP_ID);
    }

    @Test
    @DisplayName("tripId 없이 목록을 부르면 사용자 전체 티켓을 준다")
    void listsAllTicketsWhenTripIsAbsent() {
        when(ticketDAO.findByUser(USER_ID)).thenReturn(List.of(TicketReservationDTO.builder().build()));

        assertThat(service.reservations(USER_ID, null)).hasSize(1);
        verify(ticketDAO).findByUser(USER_ID);
        verify(ticketDAO, never()).findByTrip(anyLong());
    }

    @Test
    @DisplayName("산 티켓을 나중에 여행에 붙인다")
    void linksBoughtTicketToTrip() {
        when(ticketDAO.findForCancel(USER_ID, 100L)).thenReturn(Optional.of(
                TicketReservationDTO.builder().status("CONFIRMED")
                        .usageDate(LocalDate.of(2026, 9, 15)).build()));
        when(tripDAO.findById(TRIP_ID)).thenReturn(Optional.of(
                trip(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16))));
        when(ticketDAO.updateReservationTrip(USER_ID, 100L, TRIP_ID)).thenReturn(1);

        assertThat(service.linkTrip(USER_ID, 100L, TRIP_ID).getTripId()).isEqualTo(TRIP_ID);
    }

    @Test
    @DisplayName("이용일이 여행 기간 밖이면 붙이지 못한다")
    void rejectsLinkWhenUsageDateOutsideTrip() {
        when(ticketDAO.findForCancel(USER_ID, 100L)).thenReturn(Optional.of(
                TicketReservationDTO.builder().status("CONFIRMED")
                        .usageDate(LocalDate.of(2026, 9, 15)).build()));
        when(tripDAO.findById(TRIP_ID)).thenReturn(Optional.of(
                trip(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 27))));

        assertThatThrownBy(() -> service.linkTrip(USER_ID, 100L, TRIP_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_TRIP_PERIOD_MISMATCH);
        verify(ticketDAO, never()).updateReservationTrip(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("tripId를 null로 보내면 연결을 뗀다")
    void unlinksTrip() {
        when(ticketDAO.findForCancel(USER_ID, 100L)).thenReturn(Optional.of(
                TicketReservationDTO.builder().status("CONFIRMED").tripId(TRIP_ID)
                        .usageDate(LocalDate.of(2026, 9, 15)).build()));
        when(ticketDAO.updateReservationTrip(USER_ID, 100L, null)).thenReturn(1);

        assertThat(service.linkTrip(USER_ID, 100L, null).getTripId()).isNull();
        /* 뗄 때는 여행을 확인할 필요가 없다. */
        verify(tripDAO, never()).findById(anyLong());
    }
}
