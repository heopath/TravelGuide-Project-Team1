package org.example.all_my_trip_project.domain.booking.service;

import org.example.all_my_trip_project.domain.booking.dto.BookingQueueStatusResponse;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;

import java.time.Instant;

interface BookingQueueStore {

    BookingQueueStatusResponse enqueue(
            Long userId,
            CreateTicketReservationRequest request,
            String token,
            Instant now
    );

    BookingQueueStatusResponse status(Long userId, String token, Instant now);

    BookingQueueClaim claim(Long userId, String token, Instant now);

    void complete(Long userId, String token, TicketReservationDTO reservation, Instant now);

    void release(Long userId, String token, Instant now);

    void cancel(Long userId, String token);
}
