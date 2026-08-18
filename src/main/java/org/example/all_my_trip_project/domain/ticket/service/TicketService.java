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

    /**
     * 티켓을 예약한다.
     *
     * <p>여행은 <b>선택</b>이다. 관리자가 열어둔 티켓은 여행 계획과 상관없이 살 수 있다.
     * {@code tripId}를 보낸 경우에만 소유를 확인하고 이용일이 여행 기간 안인지 본다. (#255)
     */
    @Transactional
    public TicketReservationDTO reserve(Long userId, CreateTicketReservationRequest request) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        /* 여행을 보냈을 때만 확인한다. 안 보냈으면 여행에 붙지 않은 티켓이다. */
        TripDTO trip = request.tripId() == null ? null : requireOwnedTrip(userId, request.tripId());
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
        if (request.quantity() > offer.getMaxQuantityPerUser()) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        if (trip != null && !withinTrip(offer.getUsageDate(), trip)) {
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

    /**
     * 예약 목록. {@code tripId}가 없으면 그 사용자의 티켓 전체다.
     *
     * <p>여행에 붙지 않은 티켓이 생기면서 "여행별"만으로는 산 티켓을 다 볼 수 없게 됐다. (#255)
     */
    @Transactional(readOnly = true)
    public List<TicketReservationDTO> reservations(Long userId, Long tripId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (tripId == null) {
            return ticketDAO.findByUser(userId);
        }
        requireOwnedTrip(userId, tripId);
        return ticketDAO.findByTrip(tripId);
    }

    /**
     * 산 티켓을 여행에 붙이거나 뗀다. {@code tripId}가 {@code null}이면 뗀다.
     *
     * <p>붙일 때는 이용일이 여행 기간 안이어야 한다. 8월 여행에 9월 티켓을 붙이면 일정
     * 화면에서 그 티켓이 어디에도 놓이지 못한다.
     */
    @Transactional
    public TicketReservationDTO linkTrip(Long userId, Long reservationId, Long tripId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        TicketReservationDTO reservation = ticketDAO.findForCancel(userId, reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND));
        /* 취소된 예약을 여행에 붙이면 일정에 없는 티켓이 얹힌다. */
        if ("CANCELLED".equals(reservation.getStatus()) || "EXPIRED".equals(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.TICKET_CANCEL_NOT_ALLOWED);
        }
        if (tripId != null) {
            TripDTO trip = requireOwnedTrip(userId, tripId);
            if (!withinTrip(reservation.getUsageDate(), trip)) {
                throw new BusinessException(ErrorCode.TICKET_TRIP_PERIOD_MISMATCH);
            }
        }
        if (ticketDAO.updateReservationTrip(userId, reservationId, tripId) != 1) {
            throw new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND);
        }
        reservation.setTripId(tripId);
        return reservation;
    }

    private boolean withinTrip(LocalDate usageDate, TripDTO trip) {
        return usageDate != null
                && !usageDate.isBefore(trip.getStartDate())
                && !usageDate.isAfter(trip.getEndDate());
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
        /*
         * 여행에 붙은 예약만 여행 소유를 다시 본다. findForCancel이 이미 userId로 걸렀으므로
         * 여행이 없어도 남의 예약을 취소할 수는 없다.
         */
        if (reservation.getTripId() != null) {
            requireOwnedTrip(userId, reservation.getTripId());
        }

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
