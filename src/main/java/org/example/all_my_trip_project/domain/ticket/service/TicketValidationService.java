package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.ticket.dao.TicketValidationDAO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationLogDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationResponse;
import org.example.all_my_trip_project.domain.ticket.dto.ValidatableTicketDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 손님이 보여준 입장 코드를 확인하고 입장 처리한다.
 *
 * <p><b>실패도 정상 결과다.</b> 없는 코드나 이미 쓴 티켓은 요청이 잘못된 것이 아니라 검표가
 * 답해야 할 상황이다. 그래서 예외를 던지지 않고 결과를 담아 돌려준다. 현장에서는 "왜 안
 * 되는지"가 가장 중요한 정보다.
 *
 * <p><b>성공이든 실패든 기록을 남긴다.</b> 실패를 남기지 않으면 {@code ticket_validation_logs}가
 * 있을 이유가 없다. 없는 코드로 반복 시도한 것도 지문으로 묶어 볼 수 있어야 한다.
 *
 * <p><b>실패한 검표는 티켓 상태를 바꾸지 않는다.</b> 검표는 확인하는 동작이고, 유효한 티켓을
 * 쓰는 것 말고는 부수효과를 두지 않는다. 유효기간 전에 온 손님까지 {@code EXPIRED}로 만들면
 * 정작 입장 시각이 됐을 때 못 들어간다. 지난 티켓을 정리하는 것은 별도 작업이 할 일이다.
 */
@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class TicketValidationService {

    private static final String CHANNEL_DEFAULT = "ADMIN_WEB";
    private static final int MAX_LOG_SIZE = 100;
    private static final DateTimeFormatter USED_AT_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN);

    private final TicketValidationDAO ticketValidationDAO;
    /* 유효기간이 현장의 시각 기준이라 UTC가 아닌 기본 시간대를 쓴다. */
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public TicketValidationResponse validate(TicketValidationRequest request) {
        String channel = request.channel() == null || request.channel().isBlank()
                ? CHANNEL_DEFAULT
                : request.channel();
        String deviceId = text(request.deviceId());
        String fingerprint = sha256(request.token().trim());
        Long validator = currentUserId();

        ValidatableTicketDTO ticket = ticketValidationDAO.lockByTokenHash(fingerprint).orElse(null);
        if (ticket == null) {
            return record(null, validator, fingerprint, "NOT_FOUND", channel, deviceId,
                    "입장 코드에 해당하는 티켓이 없습니다.",
                    new TicketValidationResponse("NOT_FOUND", false,
                            "확인되지 않는 입장 코드예요. 코드를 다시 확인해 주세요.",
                            null, null, null, null, null, null, null));
        }

        String result = judge(ticket);
        if (!"SUCCESS".equals(result)) {
            return record(ticket.getIssuedTicketId(), validator, fingerprint, result, channel,
                    deviceId, failureReason(result, ticket), response(result, ticket, false));
        }

        /*
         * 잠갔는데도 0건이면 그 사이 다른 창구가 먼저 처리한 것이다. 성공으로 답하면 한 장으로
         * 두 명이 들어간다. 이미 사용된 것으로 돌린다.
         */
        if (ticketValidationDAO.markUsed(ticket.getIssuedTicketId()) != 1) {
            return record(ticket.getIssuedTicketId(), validator, fingerprint, "ALREADY_USED",
                    channel, deviceId, "동시에 들어온 다른 검표가 먼저 처리했습니다.",
                    response("ALREADY_USED", ticket, false));
        }

        ticket.setUsedAt(OffsetDateTime.now(clock));
        return record(ticket.getIssuedTicketId(), validator, fingerprint, "SUCCESS", channel,
                deviceId, null, response("SUCCESS", ticket, true));
    }

    @Transactional(readOnly = true)
    public List<TicketValidationLogDTO> recentLogs(String result, int limit) {
        if (limit < 1 || limit > MAX_LOG_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
        String normalized = upper(result);
        if (normalized != null && !List.of("SUCCESS", "NOT_FOUND", "ALREADY_USED", "CANCELLED",
                "EXPIRED").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
        return ticketValidationDAO.findRecentLogs(normalized, limit);
    }

    /**
     * 판정 순서가 중요하다.
     *
     * <p>취소를 먼저 본다. 취소된 예약의 티켓이 마침 유효기간도 지났다면 "기간이 지났다"보다
     * "취소된 티켓이다"가 손님에게 정확한 설명이다. 이미 사용한 것도 기간보다 먼저 본다 —
     * 어제 쓴 티켓을 오늘 가져왔을 때 "기간 지남"이라고 하면 중복 입장 시도가 가려진다.
     */
    private String judge(ValidatableTicketDTO ticket) {
        if ("CANCELLED".equals(ticket.getStatus()) || "REPLACED".equals(ticket.getStatus())
                || "CANCELLED".equals(ticket.getReservationStatus())
                || "EXPIRED".equals(ticket.getReservationStatus())) {
            return "CANCELLED";
        }
        if ("USED".equals(ticket.getStatus())) return "ALREADY_USED";
        if ("EXPIRED".equals(ticket.getStatus())) return "EXPIRED";

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (ticket.getValidFrom() != null && now.isBefore(ticket.getValidFrom())) return "EXPIRED";
        if (ticket.getValidUntil() != null && !now.isBefore(ticket.getValidUntil())) return "EXPIRED";
        return "SUCCESS";
    }

    private TicketValidationResponse response(String result, ValidatableTicketDTO ticket,
                                              boolean admitted) {
        return new TicketValidationResponse(
                result, admitted, message(result, ticket),
                ticket.getTicketNumber(), ticket.getProductName(), ticket.getOptionName(),
                ticket.getUsageDate(), ticket.getValidFrom(), ticket.getValidUntil(),
                ticket.getUsedAt());
    }

    /** 검표원이 손님에게 그대로 읽어 줄 수 있는 문장으로 쓴다. */
    private String message(String result, ValidatableTicketDTO ticket) {
        return switch (result) {
            case "SUCCESS" -> "입장하실 수 있어요.";
            case "ALREADY_USED" -> ticket.getUsedAt() == null
                    ? "이미 사용된 티켓이에요."
                    : "이미 " + local(ticket.getUsedAt()) + "에 사용된 티켓이에요.";
            case "CANCELLED" -> "취소된 티켓이에요. 입장할 수 없어요.";
            case "EXPIRED" -> expiredMessage(ticket);
            default -> "확인되지 않는 입장 코드예요.";
        };
    }

    /** 아직 이른 것과 이미 지난 것은 손님이 해야 할 일이 다르다. 기다리면 되는지 아닌지. */
    private String expiredMessage(ValidatableTicketDTO ticket) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (ticket.getValidFrom() != null && now.isBefore(ticket.getValidFrom())) {
            return "아직 입장 시간이 아니에요. "
                    + local(ticket.getValidFrom()) + "부터 입장하실 수 있어요.";
        }
        return "입장 가능 시간이 지난 티켓이에요.";
    }

    /**
     * 현장 시각으로 바꿔 찍는다.
     *
     * <p>드라이버가 돌려주는 {@code OffsetDateTime}의 오프셋이 UTC일 수 있다. 그대로 포맷하면
     * 한국 기준 9시간이 밀려, 오전 10시부터인 티켓을 <b>"01:00부터"</b>라고 읽어 주게 된다.
     * 검표원이 손님에게 그대로 말하는 문장이라 여기가 어긋나면 바로 사고가 된다.
     */
    private String local(OffsetDateTime value) {
        return USED_AT_FORMAT.format(value.atZoneSameInstant(clock.getZone()));
    }

    private String failureReason(String result, ValidatableTicketDTO ticket) {
        return switch (result) {
            case "ALREADY_USED" -> "이미 사용됨 (used_at=" + ticket.getUsedAt() + ")";
            case "CANCELLED" -> "티켓 " + ticket.getStatus()
                    + " · 예약 " + ticket.getReservationStatus();
            case "EXPIRED" -> "유효기간 밖 (" + ticket.getValidFrom() + " ~ " + ticket.getValidUntil() + ")";
            default -> null;
        };
    }

    /**
     * 기록을 남기고 응답을 돌려준다.
     *
     * <p>기록 실패가 검표를 막지 않는다. 현장에서 줄이 서 있는데 로그를 못 썼다고 입장을
     * 거부하면 그게 더 큰 문제다. 대신 무엇을 못 남겼는지 로그로 남긴다.
     */
    private TicketValidationResponse record(Long issuedTicketId, Long validator, String fingerprint,
                                            String result, String channel, String deviceId,
                                            String failureReason, TicketValidationResponse response) {
        try {
            ticketValidationDAO.insertLog(issuedTicketId, validator, fingerprint, result,
                    channel, deviceId, truncate(failureReason));
        } catch (Exception exception) {
            log.warn("검표 기록을 남기지 못했습니다. result={}, issuedTicketId={}",
                    result, issuedTicketId, exception);
        }
        return response;
    }

    /** {@code failure_reason}이 VARCHAR(500)이다. 넘치면 잘라서라도 남긴다. */
    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser user ? user.userId() : null;
    }

    /** {@code presented_token_fingerprint}가 CHAR(64)라 SHA-256 16진수와 길이가 맞는다. */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
