package org.example.all_my_trip_project.domain.ticket.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductSummaryDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.ticket.mapper.TicketMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class TicketDAO {
    private final TicketMapper mapper;

    public List<TicketOfferDTO> findOffers(String destination, LocalDate from, LocalDate to) {
        return mapper.findOffers(destination, from, to);
    }
    public Optional<TicketOfferDTO> findSlotForUpdate(Long slotId) { return mapper.findSlotForUpdate(slotId); }

    public Optional<TicketOfferDTO> findSlot(Long slotId) { return mapper.findSlot(slotId); }
    public Optional<TicketReservationDTO> findByRequestKey(Long userId, String key) {
        return mapper.findReservationByRequestKey(userId, key);
    }
    public Optional<TicketReservationDTO> findForCancel(Long userId, Long reservationId) {
        return mapper.findReservationForCancel(userId, reservationId);
    }
    public List<TicketReservationDTO> findByTrip(Long tripId) { return mapper.findReservationsByTrip(tripId); }
    public int reserveInventory(Long slotId, int quantity) { return mapper.reserveInventory(slotId, quantity); }
    public int releaseInventory(Long slotId, int quantity) { return mapper.releaseInventory(slotId, quantity); }
    public int cancelReservation(Long reservationId) { return mapper.cancelReservation(reservationId); }
    public int insertReservation(TicketReservationDTO value) { return mapper.insertReservation(value); }
    public int insertReservationItem(TicketReservationDTO value) { return mapper.insertReservationItem(value); }
    public List<TicketReservationDTO> findExpiredPending(int limit) {
        return mapper.findExpiredPendingReservations(limit);
    }
    public int expireReservation(Long reservationId) { return mapper.expireReservation(reservationId); }
    public List<String> lockIssuedTicketStatuses(Long reservationId) {
        return mapper.lockIssuedTicketStatuses(reservationId);
    }
    public int cancelIssuedTickets(Long reservationId) { return mapper.cancelIssuedTickets(reservationId); }
    public int refundPayments(Long reservationId) { return mapper.refundPayments(reservationId); }
    public int cancelConfirmedReservation(Long reservationId) {
        return mapper.cancelConfirmedReservation(reservationId);
    }
    public List<TicketReservationDTO> findByUser(Long userId) { return mapper.findReservationsByUser(userId); }

    public int updateReservationTrip(Long userId, Long reservationId, Long tripId) {
        return mapper.updateReservationTrip(userId, reservationId, tripId);
    }

    public List<TicketProductSummaryDTO> findSellableProducts(String keyword, int offset, int size) {
        return mapper.findSellableProducts(keyword, offset, size);
    }

    public long countSellableProducts(String keyword) { return mapper.countSellableProducts(keyword); }

    public Optional<TicketProductSummaryDTO> findSellableProductById(Long productId) {
        return mapper.findSellableProductById(productId);
    }

    public List<TicketOfferDTO> findSlotsByProduct(Long productId) {
        return mapper.findSlotsByProduct(productId);
    }

}