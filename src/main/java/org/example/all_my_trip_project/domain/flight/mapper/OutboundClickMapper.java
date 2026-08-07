package org.example.all_my_trip_project.domain.flight.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.flight.dto.OutboundClickDTO;

import java.util.List;

@Mapper
public interface OutboundClickMapper {

    int insert(OutboundClickDTO click);

    /** 복귀 시 가장 최근 이탈 건에 결과를 적는다. */
    int updateOutcome(@Param("flightOutboundClickId") Long flightOutboundClickId,
                      @Param("outcome") String outcome);

    /**
     * 아직 결과가 없는 이탈 이력.
     * 다음 방문에 "이 항공편, 예약하셨나요?" 배너로 다시 물어볼 대상이다.
     */
    List<OutboundClickDTO> findUnresolvedByTrip(@Param("tripId") Long tripId);
}
