package org.example.all_my_trip_project.domain.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface TicketMapper {
    List<TicketOfferDTO> findOffers(@Param("destination") String destination,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
    Optional<TicketOfferDTO> findSlotForUpdate(@Param("slotId") Long slotId);
    Optional<TicketReservationDTO> findReservationByRequestKey(@Param("userId") Long userId,
                                                                @Param("requestKey") String requestKey);
    Optional<TicketReservationDTO> findReservationForCancel(@Param("userId") Long userId,
                                                             @Param("reservationId") Long reservationId);
    List<TicketReservationDTO> findReservationsByTrip(@Param("tripId") Long tripId);
    int reserveInventory(@Param("slotId") Long slotId, @Param("quantity") int quantity);
    int releaseInventory(@Param("slotId") Long slotId, @Param("quantity") int quantity);
    int cancelReservation(@Param("reservationId") Long reservationId);
    int insertReservation(TicketReservationDTO reservation);
    int insertReservationItem(TicketReservationDTO reservation);

    /** 만료 시각이 지났는데 아직 PENDING인 예약. 예약 행과 재고 행을 함께 잠근다. */
    List<TicketReservationDTO> findExpiredPendingReservations(@Param("limit") int limit);
    int expireReservation(@Param("reservationId") Long reservationId);

    /* ── 환불 ── */

    /** 환불 대상 티켓을 잠그고 상태를 읽는다. 검표와 겹치지 않게 하려는 것이다. */
    List<String> lockIssuedTicketStatuses(@Param("reservationId") Long reservationId);
    int cancelIssuedTickets(@Param("reservationId") Long reservationId);
    int refundPayments(@Param("reservationId") Long reservationId);
    int cancelConfirmedReservation(@Param("reservationId") Long reservationId);
}
