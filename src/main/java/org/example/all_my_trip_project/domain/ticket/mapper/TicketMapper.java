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
    List<TicketReservationDTO> findReservationsByTrip(@Param("tripId") Long tripId);
    int reserveInventory(@Param("slotId") Long slotId, @Param("quantity") int quantity);
    int insertReservation(TicketReservationDTO reservation);
    int insertReservationItem(TicketReservationDTO reservation);
}
