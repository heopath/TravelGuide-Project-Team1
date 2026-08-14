package org.example.all_my_trip_project.domain.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueState;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueStatusResponse;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.ticket.service.TicketService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class BookingQueueServiceTest {

    @Mock
    private BookingQueueStore store;
    @Mock
    private TicketService ticketService;

    /* 서비스가 읽을 저장 JSON을 테스트에서 만들기 위한 것이며 서비스에 주입하지는 않는다. */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private BookingQueueService service;

    @BeforeEach
    void setUp() {
        service = new BookingQueueService(store, ticketService);
    }

    @Test
    void returnsWaitingPositionWhenCapacityIsFull() {
        CreateTicketReservationRequest request = request();
        BookingQueueStatusResponse waiting = new BookingQueueStatusResponse(
                "a".repeat(32), BookingQueueState.WAITING, 31L, 10L,
                8, 7, 4, 35, Instant.now().plusSeconds(600));
        when(store.enqueue(eq(7L), eq(request), any(), any())).thenReturn(waiting);

        assertThat(service.enqueue(7L, request)).isEqualTo(waiting);
    }

    @Test
    void reservesOnlyAfterAdmissionTokenIsClaimed() {
        String token = "b".repeat(32);
        CreateTicketReservationRequest request = request();
        TicketReservationDTO reservation = TicketReservationDTO.builder()
                .reservationId(91L).tripId(10L).slotId(31L).status("PENDING").build();
        when(store.claim(eq(7L), eq(token), any()))
                .thenReturn(new BookingQueueClaim(BookingQueueState.PROCESSING, true, request, null));
        when(ticketService.reserve(7L, request)).thenReturn(reservation);

        assertThat(service.complete(7L, token)).isSameAs(reservation);
        verify(store).complete(eq(7L), eq(token), eq(reservation), any());
    }

    @Test
    void returnsStoredResultWhenCompletionResponseWasLost() throws Exception {
        String token = "c".repeat(32);
        TicketReservationDTO reservation = TicketReservationDTO.builder()
                .reservationId(92L).tripId(10L).slotId(31L).status("PENDING").build();
        when(store.claim(eq(7L), eq(token), any())).thenReturn(new BookingQueueClaim(
                BookingQueueState.COMPLETED, false, null, objectMapper.writeValueAsString(reservation)));

        assertThat(service.complete(7L, token).getReservationId()).isEqualTo(92L);
    }

    @Test
    void returnsSuccessfulDatabaseReservationEvenIfRedisCompletionMarkFails() {
        String token = "e".repeat(32);
        CreateTicketReservationRequest request = request();
        TicketReservationDTO reservation = TicketReservationDTO.builder()
                .reservationId(93L).tripId(10L).slotId(31L).status("PENDING").build();
        when(store.claim(eq(7L), eq(token), any()))
                .thenReturn(new BookingQueueClaim(BookingQueueState.PROCESSING, true, request, null));
        when(ticketService.reserve(7L, request)).thenReturn(reservation);
        doThrow(new BusinessException(ErrorCode.BOOKING_QUEUE_UNAVAILABLE))
                .when(store).complete(eq(7L), eq(token), eq(reservation), any());

        assertThat(service.complete(7L, token)).isSameAs(reservation);
    }

    @Test
    void rejectsCompletionBeforeAdmission() {
        String token = "d".repeat(32);
        when(store.claim(eq(7L), eq(token), any()))
                .thenReturn(new BookingQueueClaim(BookingQueueState.WAITING, false, null, null));

        assertThatThrownBy(() -> service.complete(7L, token))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOOKING_QUEUE_NOT_READY));
    }

    private CreateTicketReservationRequest request() {
        return new CreateTicketReservationRequest(10L, 31L, 2, "request-1");
    }
}
