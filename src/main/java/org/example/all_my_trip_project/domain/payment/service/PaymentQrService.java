package org.example.all_my_trip_project.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentQrIssueResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentQrSummaryResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentRequest;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * QR 결제. (#281)
 *
 * <p>발권 QR(#265)과 다른 것이다. 그쪽은 <b>산 티켓으로 입장</b>하는 코드이고, 이쪽은
 * <b>결제를 받기 위한</b> 코드다. 흐름도 반대다 — 발권 QR은 손님이 보여주고 직원이 읽지만,
 * 결제 QR은 화면이 보여주고 손님이 자기 폰으로 읽는다. 매장 계산대의 QR과 같다.
 *
 * <pre>
 *   PC/태블릿 화면              손님 폰
 *   ─────────────              ────────
 *   1. QR 띄우기      →         2. 스캔해서 /pay/qr 열기
 *   4. 티켓 확인      ←         3. 금액 보고 승인
 * </pre>
 *
 * <p>승인이 실제 결제다. 승인 요청은 {@link PaymentService#pay}로 그대로 넘어가므로,
 * 결제 상태 전이·재고·발권은 카드 결제와 완전히 같은 길을 탄다. 이 클래스가 하는 일은
 * "누가 무엇을 결제하려는지"를 QR 하나로 안전하게 옮기는 것뿐이다.
 *
 * <p><b>승인은 QR을 만든 사람만 할 수 있다.</b> 토큰에 사용자가 들어 있고 승인할 때 로그인한
 * 사람과 맞춰 본다. 그래서 QR 사진이 유출돼도 남이 대신 결제해 줄 수 없고(그럴 이유도 없지만),
 * 더 중요하게는 <b>남의 티켓을 내 돈으로 결제해 버리는</b> 일이 생기지 않는다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class PaymentQrService {

    /**
     * QR이 살아 있는 시간.
     *
     * <p>결제 QR은 화면에 띄워 둔 채로 자리를 비우는 일이 흔하다. 짧게 끊어 두면 지나간
     * 화면을 나중에 본 사람이 결제 화면을 이어받지 못한다. 스캔하고 금액을 확인해 누르는 데
     * 5분이면 넉넉하고, 지나면 다시 띄우면 된다.
     */
    private static final Duration QR_TTL = Duration.ofMinutes(5);

    /** 결제수단 기록에 남을 이름. 화면 흐름이 QR일 뿐 결제 자체는 간편결제로 친다. */
    private static final String QR_EASY_PAY_PROVIDER = "QR_PAY";
    private static final String METHOD_EASY_PAY = "EASY_PAY";

    private final PaymentDAO paymentDAO;
    private final PaymentService paymentService;
    private final PaymentQrSigner signer;
    private final Clock clock = Clock.systemDefaultZone();

    /** 결제받을 QR을 띄운다. 아직 아무 일도 일어나지 않는다 — 승인해야 결제된다. */
    @Transactional(readOnly = true)
    public PaymentQrIssueResponse issue(Long userId, Long reservationId) {
        TicketReservationDTO reservation = requireOwnedReservation(userId, reservationId);
        /*
         * 결제할 수 없는 예약에는 QR을 만들지 않는다. 만들어 주면 손님이 스캔한 뒤에야
         * 안 된다는 것을 알게 된다. 승인 시점에도 다시 보지만(그 사이에 만료될 수 있다)
         * 띄우기 전에 거르는 편이 덜 헛걸음한다.
         */
        if (!"PENDING".equals(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_PAYABLE);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plus(QR_TTL);
        /*
         * 예약 만료가 QR 만료보다 먼저 오면 그쪽에 맞춘다. 자리가 반납된 뒤에도 살아 있는
         * QR을 보여주면, 스캔해서 승인한 손님이 그 자리에서 거절당한다.
         */
        OffsetDateTime reservationExpiry = reservation.getExpiresAt();
        if (reservationExpiry != null && reservationExpiry.isBefore(expiresAt)) {
            expiresAt = reservationExpiry;
        }

        return new PaymentQrIssueResponse(
                reservationId, signer.sign(reservationId, userId, expiresAt), expiresAt, now);
    }

    /** 스캔한 기기에 보여줄 내용. 금액을 확인하고 누르게 한다. */
    @Transactional(readOnly = true)
    public PaymentQrSummaryResponse summary(Long userId, String token) {
        PaymentQrSigner.Token parsed = requireToken(userId, token);
        TicketReservationDTO reservation =
                requireOwnedReservation(userId, parsed.reservationId());

        return new PaymentQrSummaryResponse(
                reservation.getReservationId(),
                reservation.getReservationNumber(),
                reservation.getProductName(),
                reservation.getOptionName(),
                reservation.getQuantity(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                !"PENDING".equals(reservation.getStatus()),
                parsed.expiresAt(),
                OffsetDateTime.now(clock));
    }

    /**
     * 승인. 여기가 실제 결제다.
     *
     * <p>멱등키를 토큰의 서명에서 만든다. 승인 버튼을 두 번 누르거나 응답이 유실돼 다시
     * 보내도 같은 키가 되어 결제는 한 번만 일어난다. 화면이 키를 만들게 하면 폰과 PC가
     * 각각 다른 키를 만들어 같은 QR로 두 번 결제될 수 있다.
     */
    @Transactional
    public PaymentResultResponse approve(Long userId, String token) {
        PaymentQrSigner.Token parsed = requireToken(userId, token);
        return paymentService.pay(userId, parsed.reservationId(),
                new PaymentRequest(METHOD_EASY_PAY, idempotencyKey(parsed), QR_EASY_PAY_PROVIDER));
    }

    /** 서명 12바이트를 그대로 쓴다. 토큰마다 다르고 100자를 넘지 않는다. */
    private String idempotencyKey(PaymentQrSigner.Token parsed) {
        return "qr-" + parsed.signature();
    }

    private PaymentQrSigner.Token requireToken(Long userId, String token) {
        PaymentQrSigner.Token parsed = signer.verify(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_QR_INVALID));
        /*
         * 만든 사람과 승인하는 사람이 달라도 위조는 아니다. 하지만 남의 QR로 내 돈이
         * 나가서는 안 되므로 거절한다.
         */
        if (!parsed.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_QR_INVALID);
        }
        if (!parsed.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.PAYMENT_QR_EXPIRED);
        }
        return parsed;
    }

    private TicketReservationDTO requireOwnedReservation(Long userId, Long reservationId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        TicketReservationDTO reservation = paymentDAO.findReservation(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND));
        if (!userId.equals(reservation.getUserId())) {
            /* 남의 예약이라는 사실조차 알리지 않는다. 없는 것과 같게 답한다. */
            throw new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND);
        }
        return reservation;
    }
}
