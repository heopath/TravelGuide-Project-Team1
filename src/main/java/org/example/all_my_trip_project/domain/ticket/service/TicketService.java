package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class TicketService {

    private final TicketDAO ticketDAO;
    private final TripDAO tripDAO;

    @Transactional(readOnly = true)
    public List<TicketOfferDTO> search(String destination, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(30).isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        String normalized = destination == null ? "" : destination.trim();
        return ticketDAO.findOffers(normalized, from, to);
    }

    @Transactional
    public TicketReservationDTO reserve(Long userId, CreateTicketReservationRequest request) {
        TripDTO trip = requireOwnedTrip(userId, request.tripId());
        String requestKey = request.requestKey().trim();

        TicketReservationDTO existing = ticketDAO.findByRequestKey(userId, requestKey).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getTripId(), request.tripId())) {
                throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
            }
            return existing;
        }

        TicketOfferDTO offer = ticketDAO.findSlotForUpdate(request.slotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        if (offer.getUsageDate().isBefore(trip.getStartDate())
                || offer.getUsageDate().isAfter(trip.getEndDate())
                || request.quantity() > offer.getMaxQuantityPerUser()) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        if (request.quantity() > offer.getRemainingQuantity()
                || ticketDAO.reserveInventory(request.slotId(), request.quantity()) != 1) {
            throw new BusinessException(ErrorCode.TICKET_NOT_AVAILABLE);
        }

        BigDecimal total = offer.getUnitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        TicketReservationDTO reservation = TicketReservationDTO.builder()
                .reservationNumber("AMT-TKT-" + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 12).toUpperCase())
                .tripId(request.tripId())
                .userId(userId)
                .status("PENDING")
                .totalAmount(total)
                .currency(offer.getCurrency())
                .requestKey(requestKey)
                .slotId(offer.getSlotId())
                .productName(offer.getProductName())
                .optionName(offer.getOptionName())
                .usageDate(offer.getUsageDate())
                .usageStartTime(offer.getStartTime())
                .quantity(request.quantity())
                .unitPrice(offer.getUnitPrice())
                .build();
        ticketDAO.insertReservation(reservation);
        ticketDAO.insertReservationItem(reservation);
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<TicketReservationDTO> reservations(Long userId, Long tripId) {
        requireOwnedTrip(userId, tripId);
        return ticketDAO.findByTrip(tripId);
    }

    private TripDTO requireOwnedTrip(Long userId, Long tripId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TripDTO trip = tripDAO.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }
}
