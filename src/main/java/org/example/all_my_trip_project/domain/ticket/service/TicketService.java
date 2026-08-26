package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketCancelResponse;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductPage;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductDetailDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductSummaryDTO;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class TicketService {

    /** 상품 목록 한 쪽의 최대 개수. 관리자 목록과 같은 값으로 맞춘다. */
    private static final int MAX_PRODUCT_PAGE_SIZE = 100;

    /** 질의가 서버 시각으로 계산해 주는 판매 상태. (#256) */
    private static final String SALE_STATE_SCHEDULED = "SCHEDULED";
    private static final String SALE_STATE_ENDED = "ENDED";

    private final TicketDAO ticketDAO;
    private final TripDAO tripDAO;
    /*
     * 오픈까지 남은 시간의 기준. 화면에 함께 내려 손님 기기 시계가 아니라 이 시각으로
     * 세게 한다. 시계가 틀어진 사람은 일찍 눌러 실패하거나 늦게 눌러 놓친다.
     */
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional(readOnly = true)
    public List<TicketOfferDTO> search(String destination, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(30).isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        String normalized = destination == null ? "" : destination.trim();
        return ticketDAO.findOffers(normalized, from, to);
    }

    /**
     * 판매 중인 상품 목록. <b>날짜를 받지 않는다.</b>
     *
     * <p>{@link #search}는 날짜 범위로 시간대를 훑는 길이고 이쪽은 상품을 훑는 길이다.
     * 관리자가 열어둔 티켓을 먼저 보여주고 언제 갈지는 상품을 고른 뒤 정한다. 날짜로 먼저
     * 거르면 팔고 있는 티켓인데도 화면이 비는 일이 생긴다 — 실제로 8월 여행에서 9월에만
     * 열린 티켓 20개가 통째로 안 보였다. (#255)
     */
    @Transactional(readOnly = true)
    public TicketProductPage products(int page, int size, String keyword) {
        if (page < 0 || size < 1 || size > MAX_PRODUCT_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        /*
         * 빈 검색어는 null이 아니라 빈 문자열로 넘긴다.
         *
         * 질의가 #{keyword}를 IS NULL 비교에 쓰는데, 타입 없는 null 파라미터가 들어가면
         * PostgreSQL이 파라미터 타입을 정하지 못해 질의 자체가 실패한다. search()가 같은
         * 이유로 destination을 빈 문자열로 맞춘다.
         */
        String normalized = keyword == null ? "" : keyword.trim();
        long total = ticketDAO.countSellableProducts(normalized);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new TicketProductPage(
                ticketDAO.findSellableProducts(normalized, offset, size), page, size, total, totalPages,
                OffsetDateTime.now(clock));
    }

    /**
     * 상품 하나와 그 상품에서 고를 수 있는 시간대 전부.
     *
     * <p>여기에는 30일 제한을 두지 않는다. 상품을 이미 골랐으므로 그 상품의 일정을 다
     * 보여주는 것이 맞다. 제한은 "아무 조건 없이 전체 시간대를 훑는" 것을 막으려던 것이고,
     * 상품 하나로 좁혀진 뒤에는 그 이유가 사라진다.
     */
    @Transactional(readOnly = true)
    public TicketProductDetailDTO product(Long productId) {
        if (productId == null || productId < 1) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        TicketProductSummaryDTO product = ticketDAO.findSellableProductById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        return new TicketProductDetailDTO(
                product, ticketDAO.findSlotsByProduct(productId), OffsetDateTime.now(clock));
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
        /*
         * 오픈 전에는 잡아 두지 못하게 막는다. (#256)
         *
         * 목록에서 오픈 예정으로 보여주는 상품이라 버튼을 눌러도 여기까지는 온다. 조회에서만
         * 막으면 API를 직접 부르는 쪽이 그대로 뚫는다.
         */
        requireOnSale(offer);
        if (request.quantity() > offer.getMaxQuantityPerUser()) {
            throw new BusinessException(ErrorCode.TICKET_QUANTITY_EXCEEDED);
        }
        if (trip != null && !withinTrip(offer.getUsageDate(), trip)) {
            throw new BusinessException(ErrorCode.TICKET_DATE_OUTSIDE_TRIP);
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
        if (request.tripId() != null) {
            tripDAO.clearBookingConfirmation(request.tripId());
        }
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
        Long previousTripId = reservation.getTripId();
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
        if (previousTripId != null) {
            tripDAO.clearBookingConfirmation(previousTripId);
        }
        if (tripId != null && !tripId.equals(previousTripId)) {
            tripDAO.clearBookingConfirmation(tripId);
        }
        reservation.setTripId(tripId);
        return reservation;
    }

    /**
     * 지금 살 수 있는 상품인지. (#256)
     *
     * <p>상태는 질의가 서버 시각으로 계산해 넣어 준다. 여기서 다시 시각을 비교하지 않는 이유는,
     * 잠근 행을 읽은 순간과 계산 시점이 갈리면 같은 요청 안에서 판단이 두 벌 생기기 때문이다.
     *
     * <p>오픈 전과 판매 종료를 갈라서 알린다 — 손님에게 해 줄 말이 다르다. 하나는 "그 시각에
     * 다시 오세요"이고 다른 하나는 "이제 못 삽니다"이다.
     */
    /**
     * 대기열에 서기 전 판매 상태만 본다. (#256)
     *
     * <p>오픈 전에 줄부터 서게 두면, 승급된 뒤 예약 단계에서야 거절당한다. 그때는 이미 자리를
     * 기다린 시간이 버려진 뒤다. 줄 서는 자리에서 미리 막는다.
     *
     * <p>여기서 통과해도 예약할 때 다시 본다. 줄을 서 있는 동안 판매가 끝날 수 있다.
     */
    @Transactional(readOnly = true)
    public void requireSaleOpen(Long slotId) {
        TicketOfferDTO offer = ticketDAO.findSlot(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        requireOnSale(offer);
    }

    private void requireOnSale(TicketOfferDTO offer) {
        if (SALE_STATE_SCHEDULED.equals(offer.getSaleState())) {
            throw new BusinessException(ErrorCode.TICKET_SALE_NOT_OPEN);
        }
        if (SALE_STATE_ENDED.equals(offer.getSaleState())) {
            throw new BusinessException(ErrorCode.TICKET_SALE_ENDED);
        }
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
        TicketCancelResponse response;
        if ("PENDING".equals(reservation.getStatus())) response = cancelPending(reservation);
        else if ("CONFIRMED".equals(reservation.getStatus())) response = refund(reservation);
        else throw new BusinessException(ErrorCode.TICKET_CANCEL_NOT_ALLOWED);
        if (reservation.getTripId() != null) {
            tripDAO.clearBookingConfirmation(reservation.getTripId());
        }
        return response;
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
