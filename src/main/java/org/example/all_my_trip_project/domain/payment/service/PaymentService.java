package org.example.all_my_trip_project.domain.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.IssuedTicketDTO;
import org.example.all_my_trip_project.domain.payment.dto.PayableReservationDTO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentDTO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentRequest;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 티켓 모의 결제와 발권.
 *
 * <p><b>실제 돈이 오가지 않습니다.</b> {@code provider}는 항상 {@code MOCK}이고, 요청과 승인이
 * 같은 순간에 끝납니다. 실제 PG를 붙이면 승인 콜백을 기다리는 단계가 생깁니다.
 *
 * <p>결제·확정·발권을 <b>한 트랜잭션</b>에서 끝냅니다. 셋 중 하나만 성공한 상태는 어느 것도
 * 쓸모가 없습니다. 결제됐는데 예약이 PENDING이면 만료 정리가 자리를 회수해 가고, 확정됐는데
 * 티켓이 없으면 산 사람이 들어갈 수단이 없습니다.
 */
@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class PaymentService {

    private static final String PROVIDER_MOCK = "MOCK";
    private static final int TOKEN_BYTES = 32;

    /**
     * 티켓을 언제부터 언제까지 쓸 수 있는지.
     *
     * <p>이용 시작 시각이 없는 상품(종일권)은 그날 0시부터로 본다. 끝은 이용일 다음 날 0시다.
     * 상품이 시간대를 나눠 팔더라도 입장은 그날 안에서 유연하게 두는 편이 현장에서 덜 막힌다.
     */
    private static final LocalTime DEFAULT_VALID_FROM = LocalTime.MIDNIGHT;

    private final PaymentDAO paymentDAO;
    /*
     * systemUTC가 아니라 기본 시간대를 쓴다. usage_date는 현장의 달력 날짜라, UTC로 읽으면
     * 한국 기준 9시간이 밀려 유효기간이 하루 어긋난다. 운영·로컬 모두 Asia/Seoul이다.
     */
    private final Clock clock = Clock.systemDefaultZone();
    private final SecureRandom random = new SecureRandom();

    /**
     * 결제하고 그 자리에서 발권한다.
     *
     * <p>같은 멱등키로 다시 들어오면 결제를 새로 만들지 않고 앞의 결과를 그대로 돌려준다.
     * 결제 버튼을 두 번 누르거나 응답이 유실되어 재시도하는 경우다.
     */
    @Transactional
    public PaymentResultResponse pay(Long userId, Long reservationId, PaymentRequest request) {
        requireUser(userId);
        if (reservationId == null || reservationId < 1) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }
        String idempotencyKey = request.idempotencyKey().trim();

        PaymentResultResponse replayed = replay(userId, idempotencyKey, reservationId);
        if (replayed != null) return replayed;

        PayableReservationDTO reservation = paymentDAO.lockPayableReservation(userId, reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND));
        requirePayable(reservation);

        PaymentDTO payment = PaymentDTO.builder()
                .reservationId(reservationId)
                .idempotencyKey(idempotencyKey)
                .provider(PROVIDER_MOCK)
                .method(request.method().toUpperCase(Locale.ROOT))
                .amount(reservation.getTotalAmount())
                .currencyCode(reservation.getCurrencyCode())
                .build();

        try {
            paymentDAO.insertPayment(payment);
        } catch (DuplicateKeyException exception) {
            /*
             * 같은 멱등키 두 건이 동시에 들어왔다. 위의 replay 조회는 앞 트랜잭션이 커밋되기
             * 전이라 못 봤고, UNIQUE 제약이 마지막에 걸러 준다. 이때는 실패가 아니라
             * "이미 결제됨"이므로 앞의 결과를 돌려준다.
             */
            PaymentResultResponse afterRace = replay(userId, idempotencyKey, reservationId);
            if (afterRace != null) return afterRace;
            throw exception;
        }

        if (paymentDAO.confirmReservation(reservationId) != 1) {
            /* 잠갔는데도 PENDING이 아니게 됐다면 만료 정리가 먼저 가져간 것이다. */
            throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        }

        List<IssuedTicketDTO> tickets = issue(reservation);
        /*
         * 넣은 객체가 아니라 DB에서 다시 읽어 돌려준다. status·승인 시각·발급 시각은 DB가
         * 채우므로, INSERT에 쓴 객체를 그대로 내보내면 그 칸들이 null인 채 응답에 실린다.
         * 화면은 payload만 보고 결제가 끝났는지 알 수 없게 된다.
         */
        return new PaymentResultResponse(
                paymentDAO.findPayment(payment.getPaymentId()).orElse(payment),
                requireReservation(reservationId),
                withIssuedAt(tickets, reservationId),
                false);
    }

    @Transactional(readOnly = true)
    public List<IssuedTicketDTO> tickets(Long userId, Long reservationId) {
        requireUser(userId);
        TicketReservationDTO reservation = requireReservation(reservationId);
        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND);
        }
        return paymentDAO.findTicketsByReservation(reservationId);
    }

    /**
     * 이미 같은 멱등키로 결제된 건이 있으면 그 결과를 되살린다.
     *
     * <p>다른 예약에 쓰인 키로 들어오면 거부한다. 그대로 통과시키면 A를 결제한 응답을 받고
     * B가 결제됐다고 믿게 된다.
     */
    private PaymentResultResponse replay(Long userId, String idempotencyKey, Long reservationId) {
        PaymentDTO previous = paymentDAO.findByIdempotencyKey(userId, idempotencyKey).orElse(null);
        if (previous == null) return null;
        if (!previous.getReservationId().equals(reservationId)) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }
        return new PaymentResultResponse(
                previous,
                requireReservation(previous.getReservationId()),
                paymentDAO.findTicketsByReservation(previous.getReservationId()),
                true);
    }

    private void requirePayable(PayableReservationDTO reservation) {
        String status = reservation.getStatus();
        if ("EXPIRED".equals(status)) throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        if (!"PENDING".equals(status)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_PAYABLE);
        }
        /*
         * 만료 시각이 지났으면 정리 작업이 아직 안 왔더라도 결제를 막는다. 여기서 통과시키면
         * 결제 직후 정리가 그 예약을 EXPIRED로 돌려 돈만 받고 자리는 없는 상태가 된다.
         */
        OffsetDateTime expiresAt = reservation.getExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        }
    }

    /**
     * 수량만큼 티켓을 발급한다.
     *
     * <p>한 장씩 만드는 것은 입장이 한 사람씩 이뤄지기 때문이다. 2매를 한 장으로 묶으면
     * 일행이 따로 들어갈 수 없고, 한 명만 입장한 상태를 표시할 방법도 없다.
     */
    private List<IssuedTicketDTO> issue(PayableReservationDTO reservation) {
        LocalTime startTime = reservation.getUsageStartTime() == null
                ? DEFAULT_VALID_FROM
                : reservation.getUsageStartTime();
        ZoneId zone = clock.getZone();
        OffsetDateTime validFrom = reservation.getUsageDate().atTime(startTime).atZone(zone).toOffsetDateTime();
        OffsetDateTime validUntil = reservation.getUsageDate().plusDays(1)
                .atStartOfDay(zone).toOffsetDateTime();

        int quantity = reservation.getQuantity() == null ? 1 : reservation.getQuantity();
        List<IssuedTicketDTO> issued = new ArrayList<>(quantity);
        for (int index = 0; index < quantity; index += 1) {
            String token = newToken();
            IssuedTicketDTO ticket = IssuedTicketDTO.builder()
                    .reservationItemId(reservation.getReservationItemId())
                    .ticketNumber(newTicketNumber())
                    .issueMethod("MOBILE")
                    .status("ISSUED")
                    .validFrom(validFrom)
                    .validUntil(validUntil)
                    .verificationToken(token)
                    .build();
            paymentDAO.insertIssuedTicket(ticket, sha256(token));
            issued.add(ticket);
        }
        return issued;
    }

    /**
     * 발급 시각을 DB에서 채워 넣는다. 검증 토큰은 조회 SQL이 가져오지 않으므로 방금 만든
     * 값에서 다시 붙인다.
     *
     * <p>토큰을 조회로 가져오게 만들지 않는 것이 요점이다. 그러면 티켓 목록을 부르는 모든
     * 자리에 입장 코드가 딸려 나온다. 원문은 발급하는 이 순간에만 존재한다.
     */
    private List<IssuedTicketDTO> withIssuedAt(List<IssuedTicketDTO> issued, Long reservationId) {
        Map<String, IssuedTicketDTO> stored = paymentDAO.findTicketsByReservation(reservationId)
                .stream()
                .collect(Collectors.toMap(IssuedTicketDTO::getTicketNumber, ticket -> ticket,
                        (first, second) -> first));
        for (IssuedTicketDTO ticket : issued) {
            IssuedTicketDTO saved = stored.get(ticket.getTicketNumber());
            if (saved != null) ticket.setIssuedAt(saved.getIssuedAt());
        }
        return issued;
    }

    /** 입장 코드. 추측할 수 없어야 하므로 난수에서 만든다. */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 사람이 부를 수 있는 번호. 티켓을 특정하는 값이지 입장 자격이 아니다.
     * 입장은 {@link #newToken()}으로만 확인한다.
     */
    private String newTicketNumber() {
        return "AMT-TKN-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    /** {@code verification_token_hash}가 CHAR(64)라 SHA-256 16진수와 길이가 맞는다. */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            /* SHA-256은 표준 구현에 반드시 있다. 여기 오면 JVM 문제다. */
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private TicketReservationDTO requireReservation(Long reservationId) {
        return paymentDAO.findReservation(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND));
    }

    private void requireUser(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
