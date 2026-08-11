package org.example.all_my_trip_project.domain.accommodation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOutboundClickDTO;

import java.util.List;

@Mapper
public interface AccommodationOutboundClickMapper {

    int insert(AccommodationOutboundClickDTO click);

    /** 복귀 시 해당 이탈 건에 결과를 적는다. */
    int updateOutcome(@Param("accommodationOutboundClickId") Long accommodationOutboundClickId,
                      @Param("outcome") String outcome);

    /**
     * 아직 결과가 없는 이탈 이력.
     *
     * <p>다음 방문에 "이 숙소 예약하셨나요?" 배너로 다시 물어볼 대상이다.
     * 담아둔 숙소를 지우면 이력도 함께 지워지므로(FK CASCADE) 사라진 숙소를 묻는 일은 없다.
     */
    List<AccommodationOutboundClickDTO> findUnresolvedByTrip(@Param("tripId") Long tripId);
}
