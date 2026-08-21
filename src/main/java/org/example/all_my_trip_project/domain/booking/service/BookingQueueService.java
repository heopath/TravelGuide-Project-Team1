package org.example.all_my_trip_project.domain.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueState;
import org.example.all_my_trip_project.domain.booking.dto.BookingQueueStatusResponse;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.domain.ticket.service.TicketService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class BookingQueueService {

    private final BookingQueueStore store;
    private final TicketService ticketService;
    // Spring Boot 4는 Jackson 3을 자동 구성하므로 Jackson 2의 ObjectMapper 빈이 없다.
    // 주입받으면 ui 이외의 프로필에서 컨텍스트가 뜨지 않는다. 다른 서비스와 같이 직접 만든다.
    // 예약 DTO에 LocalDate·LocalTime이 있어 모듈 등록 없이는 역직렬화가 실패한다.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.systemUTC();

    public BookingQueueStatusResponse enqueue(Long userId, CreateTicketReservationRequest request) {
        requireUser(userId);
        /*
         * 오픈 전에는 줄도 세우지 않는다. (#256)
         *
         * 지정 시각 판매는 오픈 전에도 목록에 보이므로 버튼을 눌러 여기까지 온다. 줄부터
         * 서게 두면 승급된 뒤 예약 단계에서야 거절당하고, 그때는 기다린 시간이 버려진 뒤다.
         */
        ticketService.requireSaleOpen(request.slotId());
        String token = UUID.randomUUID().toString().replace("-", "");
        return store.enqueue(userId, request, token, Instant.now(clock));
    }

    public BookingQueueStatusResponse status(Long userId, String token) {
        requireUser(userId);
        return store.status(userId, requireToken(token), Instant.now(clock));
    }

    public TicketReservationDTO complete(Long userId, String token) {
        requireUser(userId);
        String normalizedToken = requireToken(token);
        BookingQueueClaim claim = store.claim(userId, normalizedToken, Instant.now(clock));
        if (claim.state() == BookingQueueState.COMPLETED) {
            return completedReservation(claim.completedReservationJson());
        }
        if (!claim.claimed()) {
            ErrorCode errorCode = claim.state() == BookingQueueState.PROCESSING
                    ? ErrorCode.BOOKING_QUEUE_PROCESSING
                    : ErrorCode.BOOKING_QUEUE_NOT_READY;
            throw new BusinessException(errorCode);
        }

        TicketReservationDTO reservation;
        try {
            reservation = ticketService.reserve(userId, claim.request());
        } catch (BusinessException exception) {
            cancelAfterFailure(userId, normalizedToken);
            throw exception;
        } catch (RuntimeException exception) {
            releaseAfterFailure(userId, normalizedToken);
            throw exception;
        }

        try {
            store.complete(userId, normalizedToken, reservation, Instant.now(clock));
        } catch (RuntimeException exception) {
            // DB 예약은 이미 커밋될 수 있다. Redis 완료 표시에 실패했다고 사용자에게
            // 예약 실패로 답하면 재시도를 유발하므로, 예약 결과는 성공으로 돌려준다.
            log.warn("Ticket reservation succeeded but queue completion state was not saved. token={}",
                    normalizedToken, exception);
        }
        return reservation;
    }

    public void cancel(Long userId, String token) {
        requireUser(userId);
        store.cancel(userId, requireToken(token));
    }

    private TicketReservationDTO completedReservation(String json) {
        try {
            return objectMapper.readValue(json, TicketReservationDTO.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BOOKING_QUEUE_UNAVAILABLE);
        }
    }

    private void cancelAfterFailure(Long userId, String token) {
        try {
            store.cancel(userId, token);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to remove rejected booking queue entry. token={}", token, cleanupFailure);
        }
    }

    private void releaseAfterFailure(Long userId, String token) {
        try {
            store.release(userId, token, Instant.now(clock));
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to release booking queue claim. token={}", token, cleanupFailure);
        }
    }

    private void requireUser(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private String requireToken(String token) {
        String normalized = token == null ? "" : token.trim();
        if (!normalized.matches("[a-f0-9]{32}")) {
            throw new BusinessException(ErrorCode.BOOKING_QUEUE_EXPIRED);
        }
        return normalized;
    }
}
