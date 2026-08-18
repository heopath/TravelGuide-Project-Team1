package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketCancelResponse;
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

    /**
     * 예약을 취소한다. 결제 전이면 자리만 놓고, 결제 후면 환불까지 한다.
     *
     * <p>손님에게는 둘 다 "예약 취소" 하나다. 돈이 돌아오는지는 결제했는지에 따라 갈릴 뿐이라
     * 경로를 나누지 않는다.
     *
     * <p>결제한 예약을 취소할 때는 <b>네 가지가 함께 움직인다.</b> 결제를 환불로, 발급된
     * 티켓을 무효로, 예약을 취소로, 그리고 잡아 두었던 수량을 반납한다. 하나라도 빠지면
     * 어긋난다 — 티켓을 무효로 만들지 않으면 환불받고도 입장할 수 있고, 재고를 반납하지
     * 않으면 판 적 없는 자리가 잠긴다.
     */
    @Transactional
    public TicketCancelResponse cancel(Long userId, Long reservationId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        TicketReservationDTO reservation = ticketDAO.findForCancel(userId, reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND));
        requireOwnedTrip(userId, reservation.getTripId());

        if ("CANCELLED".equals(reservation.getStatus())) {
            return new TicketCancelResponse(reservation, false, 0);
        }
        if ("PENDING".equals(reservation.getStatus())) return cancelPending(reservation);
        if ("CONFIRMED".equals(reservation.getStatus())) return refund(reservation);
        throw new BusinessException(ErrorCode.TICKET_CANCEL_NOT_ALLOWED);
    }

    private TicketCancelResponse cancelPending(TicketReservationDTO reservation) {
        if (ticketDAO.cancelReservation(reservation.getReservationId()) != 1
                || ticketDAO.releaseInventory(reservation.getSlotId(), reservation.getQuantity()) != 1) {
            throw new BusinessException(ErrorCode.TICKET_CANCEL_NOT_ALLOWED);
        }
        reservation.setStatus("CANCELLED");
        return new TicketCancelResponse(reservation, false, 0);
    }

    private TicketCancelResponse refund(TicketReservationDTO reservation) {
        Long reservationId = reservation.getReservationId();

        /*
         * 이용일이 지난 뒤의 취소는 받지 않는다. 오지 않은 것은 환불 대상이 아니다.
         * 당일까지는 허용한다 — 아침에 마음이 바뀌는 것까지 막을 이유는 없다.
         */
        if (reservation.getUsageDate() != null
                && reservation.getUsageDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.TICKET_USAGE_DATE_PASSED);
        }

        /*
         * 티켓 행을 잠그고 상태를 본다. 잠그지 않으면 여기서 "안 썼다"를 읽은 뒤 환불을
         * 끝내기 전에 검표가 들어와 그 티켓이 USED가 될 수 있다. 입장하고 환불도 받는 셈이다.
         *
         * 한 장이라도 썼으면 거부한다. 2매 중 1매만 쓴 경우도 마찬가지다 — 부분 환불은
         * 범위 밖이라, 쓴 만큼만 빼고 돌려줄 방법이 없다.
         */
        if (ticketDAO.lockIssuedTicketStatuses(reservationId).contains("USED")) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }

        if (ticketDAO.cancelConfirmedReservation(reservationId) != 1) {
            /* 잠갔는데도 CONFIRMED가 아니게 됐다면 다른 요청이 먼저 처리한 것이다. */
            throw new BusinessException(ErrorCode.TICKET_CANCEL_NOT_ALLOWED);
        }
        int cancelledTickets = ticketDAO.cancelIssuedTickets(reservationId);
        ticketDAO.refundPayments(reservationId);
        if (ticketDAO.releaseInventory(reservation.getSlotId(), reservation.getQuantity()) != 1) {
            /*
             * 재고를 되돌리지 못하면 조용히 넘기지 않는다. 예약만 취소되고 자리는 잠긴 채
             * 남아 아무도 그 자리를 살 수 없게 된다.
             */
            throw new IllegalStateException(
                    "예약 " + reservationId + "의 재고를 되돌리지 못했습니다.");
        }

        reservation.setStatus("CANCELLED");
        return new TicketCancelResponse(reservation, true, cancelledTickets);
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
