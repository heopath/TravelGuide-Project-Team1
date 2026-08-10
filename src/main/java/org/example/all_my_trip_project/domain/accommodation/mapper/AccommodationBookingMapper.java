package org.example.all_my_trip_project.domain.accommodation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AccommodationBookingMapper {

    /** 같은 (trip, 기간)을 다시 고르면 새 행을 만들지 않고 스냅샷을 갈아끼운다. */
    int upsertSelection(AccommodationBookingDTO booking);

    List<AccommodationBookingDTO> findByTrip(@Param("tripId") Long tripId);

    Optional<AccommodationBookingDTO> findById(@Param("accommodationBookingId") Long accommodationBookingId);

    /**
     * 기간이 겹치는 다른 예약을 찾는다.
     *
     * <p>DB에 EXCLUDE 제약을 걸지 않았으므로(확장을 늘리지 않으려고) 여기서 확인한다.
     * 같은 기간은 UNIQUE 인덱스가 막지만 "8/10\~8/12"와 "8/11\~8/13"처럼 겹치는 경우는 못 막는다.
     */
    List<AccommodationBookingDTO> findOverlapping(@Param("tripId") Long tripId,
                                                  @Param("checkIn") LocalDate checkIn,
                                                  @Param("checkOut") LocalDate checkOut);

    int delete(@Param("accommodationBookingId") Long accommodationBookingId);
}
