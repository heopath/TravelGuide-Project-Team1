package org.example.all_my_trip_project.domain.booking.service;

import org.example.all_my_trip_project.domain.booking.dto.BookingQueueState;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;

record BookingQueueClaim(
        BookingQueueState state,
        boolean claimed,
        CreateTicketReservationRequest request,
        String completedReservationJson
) {
}
