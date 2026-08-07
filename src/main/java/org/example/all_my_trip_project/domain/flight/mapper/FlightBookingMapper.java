package org.example.all_my_trip_project.domain.flight.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FlightBookingMapper {

    /** 같은 (trip, leg)를 다시 고르면 새 행을 만들지 않고 운임 스냅샷을 갈아끼운다. */
    int upsertSelection(FlightBookingDTO booking);

    Optional<FlightBookingDTO> findByTripAndLeg(@Param("tripId") Long tripId, @Param("leg") int leg);

    List<FlightBookingDTO> findByTrip(@Param("tripId") Long tripId);

    int updateUserReported(@Param("tripId") Long tripId,
                           @Param("leg") int leg,
                           @Param("userReportedBooked") boolean userReportedBooked);

    int updateBookingRef(@Param("tripId") Long tripId,
                         @Param("leg") int leg,
                         @Param("bookingRef") String bookingRef);

    int delete(@Param("tripId") Long tripId, @Param("leg") int leg);
}
