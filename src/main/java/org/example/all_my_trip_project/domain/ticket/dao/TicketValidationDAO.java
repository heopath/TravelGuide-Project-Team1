package org.example.all_my_trip_project.domain.ticket.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationLogDTO;
import org.example.all_my_trip_project.domain.ticket.dto.ValidatableTicketDTO;
import org.example.all_my_trip_project.domain.ticket.mapper.TicketValidationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class TicketValidationDAO {

    private final TicketValidationMapper mapper;

    public Optional<ValidatableTicketDTO> lockByTokenHash(String tokenHash) {
        return mapper.lockByTokenHash(tokenHash);
    }

    public int markUsed(Long issuedTicketId) {
        return mapper.markUsed(issuedTicketId);
    }

    public int insertLog(Long issuedTicketId, Long validatorUserId, String fingerprint,
                         String result, String channel, String deviceId, String failureReason) {
        return mapper.insertLog(issuedTicketId, validatorUserId, fingerprint,
                result, channel, deviceId, failureReason);
    }

    public List<TicketValidationLogDTO> findRecentLogs(String result, int limit) {
        return mapper.findRecentLogs(result, limit);
    }
}
