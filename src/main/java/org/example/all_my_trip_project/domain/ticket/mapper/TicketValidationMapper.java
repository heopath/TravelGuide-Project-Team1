package org.example.all_my_trip_project.domain.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationLogDTO;
import org.example.all_my_trip_project.domain.ticket.dto.ValidatableTicketDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TicketValidationMapper {

    /**
     * 입장 코드 해시로 티켓을 찾아 잠근다.
     *
     * <p>잠그지 않으면 같은 코드를 두 창구에서 동시에 넣었을 때 둘 다 "사용 안 됨"을 보고
     * 둘 다 입장시킨다. 한 장으로 두 명이 들어가는 셈이다.
     */
    Optional<ValidatableTicketDTO> lockByTokenHash(@Param("tokenHash") String tokenHash);

    /** {@code ISSUED}일 때만 바꾼다. 잠금으로 걸러지지만 조건을 남겨 두 번 처리되지 않게 한다. */
    int markUsed(@Param("issuedTicketId") Long issuedTicketId);

    /** 성공이든 실패든 남긴다. 실패를 남기지 않으면 이 표가 있을 이유가 없다. */
    int insertLog(@Param("issuedTicketId") Long issuedTicketId,
                  @Param("validatorUserId") Long validatorUserId,
                  @Param("fingerprint") String fingerprint,
                  @Param("result") String result,
                  @Param("channel") String channel,
                  @Param("deviceId") String deviceId,
                  @Param("failureReason") String failureReason);

    List<TicketValidationLogDTO> findRecentLogs(@Param("result") String result,
                                                @Param("limit") int limit);
}
