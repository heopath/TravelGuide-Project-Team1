package org.example.all_my_trip_project.domain.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.KakaoPayReadyResponse;
import org.example.all_my_trip_project.domain.payment.dto.PaymentRequest;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 카카오페이 결제. (#281)
 *
 * <p>토스와 달리 결제창을 우리 화면 안에 띄우지 못한다. 카카오가 준 주소로 <b>손님을 아예
 * 보냈다가</b> 돌려받는 방식이다. 그래서 단계가 둘이다.
 *
 * <pre>
 *   화면              우리 서버                    카카오페이
 *   ────              ────────                    ──────────
 *   결제 시작    →    ready 호출              →   거래번호(tid)와 결제 주소를 준다
 *   결제 주소로 이동  ─────────────────────────→   손님이 카카오톡·카드로 인증
 *                     approval_url로 복귀     ←    pg_token을 붙여 돌려보낸다
 *   승인 요청    →    approve 호출            →   실제 결제
 * </pre>
 *
 * <p><b>거래번호는 서버가 들고 있는다.</b> 화면에 내려 줬다가 되돌려받으면, 다른 결제의
 * 거래번호를 끼워 넣어 승인시키는 길이 열린다. 손님 한 명당 진행 중인 결제는 하나이므로
 * 사용자 번호로 Redis에 잠깐 둔다.
 *
 * <p>테스트 가맹점({@code TC0ONETIME})으로는 실제 돈이 빠져나가지 않는다.
 */
@Slf4j
@Service
@Profile("!ui")
public class KakaoPayService {

    private static final String READY_URL = "https://open-api.kakaopay.com/online/v1/payment/ready";
    private static final String APPROVE_URL = "https://open-api.kakaopay.com/online/v1/payment/approve";

    /** 결제 기록에 남을 결제사. 모의 결제({@code MOCK})와 갈리는 값이다. */
    private static final String ACQUIRER = "KAKAO";

    /**
     * 결제를 시작한 기록이 살아 있는 시간.
     *
     * <p>카카오페이 결제창 자체가 15분쯤 뒤 만료된다. 그보다 길게 두면 이미 죽은 거래번호로
     * 승인을 시도해 카카오 쪽 실패만 쌓인다.
     */
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

    private static final String SESSION_PREFIX = "all-my-trips:payment:kakao:";

    private final PaymentService paymentService;
    private final PaymentDAO paymentDAO;
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;
    private final String secretKey;
    private final String cid;
    private final String baseUrl;

    public KakaoPayService(PaymentService paymentService,
                           PaymentDAO paymentDAO,
                           StringRedisTemplate redisTemplate,
                           RestClient.Builder restClientBuilder,
                           @Value("${payment.kakao.secret-key:}") String secretKey,
                           @Value("${payment.kakao.cid:TC0ONETIME}") String cid,
                           @Value("${payment.kakao.base-url:http://localhost:8080}") String baseUrl) {
        this.paymentService = paymentService;
        this.paymentDAO = paymentDAO;
        this.redisTemplate = redisTemplate;
        this.restClient = restClientBuilder.build();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.cid = cid;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 시크릿 키가 없으면 카카오를 부를 수 없다. 화면이 미리 알고 목록에서 빼게 한다. */
    public boolean isConfigured() {
        return !secretKey.isBlank();
    }

    /**
     * 결제를 시작하고 손님을 보낼 주소를 돌려준다.
     *
     * <p>금액과 상품명은 <b>예약에서 읽는다.</b> 화면이 준 값을 그대로 넘기면 1원짜리
     * 결제창을 띄우고 4만원짜리 티켓을 받아 갈 수 있다.
     */
    public KakaoPayReadyResponse ready(Long userId, Long reservationId) {
        requireConfigured();
        TicketReservationDTO reservation = requireOwnReservation(userId, reservationId);

        String orderId = orderIdOf(reservationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", cid);
        body.put("partner_order_id", orderId);
        body.put("partner_user_id", String.valueOf(userId));
        body.put("item_name", itemNameOf(reservation));
        body.put("quantity", reservation.getQuantity() == null ? 1 : reservation.getQuantity());
        body.put("total_amount", amountOf(reservation));
        /* 면세 금액. 티켓은 과세 상품이라 0이다. */
        body.put("tax_free_amount", 0);
        body.put("approval_url", baseUrl + "/pay/kakao");
        body.put("cancel_url", baseUrl + "/pay/kakao?result=cancel");
        body.put("fail_url", baseUrl + "/pay/kakao?result=fail");

        Map<String, Object> ready = call(READY_URL, body);
        String tid = String.valueOf(ready.get("tid"));
        String redirectUrl = String.valueOf(ready.get("next_redirect_pc_url"));
        if (tid.isBlank() || "null".equals(tid) || "null".equals(redirectUrl)) {
            log.warn("카카오페이 ready 응답에 거래번호나 결제 주소가 없습니다: {}", ready.keySet());
            throw new BusinessException(ErrorCode.KAKAO_PAY_CALL_FAILED);
        }

        remember(userId, tid, orderId, reservationId);
        return new KakaoPayReadyResponse(redirectUrl);
    }

    /**
     * 카카오에서 돌아온 결제를 승인하고 우리 결제로 기록한다.
     *
     * <p>화면이 주는 것은 {@code pg_token} 하나다. <b>어느 예약을 결제하는지는 화면에게 묻지
     * 않는다</b> — 결제를 시작할 때 서버가 적어 둔 기록에서 꺼낸다.
     *
     * <p>멱등키로 거래번호를 쓴다. 결제 하나에 하나뿐인 값이라 돌아오는 주소를 두 번 열거나
     * 새로고침해도 결제가 두 번 만들어지지 않는다.
     */
    @Transactional
    public PaymentResultResponse approve(Long userId, String pgToken) {
        requireConfigured();

        String[] session = recall(userId);
        String tid = session[0];
        String orderId = session[1];
        Long reservationId = Long.valueOf(session[2]);

        TicketReservationDTO reservation = requireOwnReservation(userId, reservationId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", cid);
        body.put("tid", tid);
        body.put("partner_order_id", orderId);
        body.put("partner_user_id", String.valueOf(userId));
        body.put("pg_token", pgToken);

        Map<String, Object> approved = call(APPROVE_URL, body);
        requireSameAmount(reservation, approved);

        /*
         * 승인이 끝났으니 기록을 지운다. 남겨 두면 새로고침이 같은 거래번호로 카카오를 다시
         * 부르고, 이미 승인된 거래라 카카오 쪽 실패만 쌓인다. 우리 결제는 멱등키가 막지만
         * 바깥에 부질없는 요청을 보내지 않는 편이 낫다.
         */
        forget(userId);

        /*
         * 카드로 결제했더라도 우리 기록에는 간편결제(카카오페이)로 남긴다. 손님이 고른 것도,
         * 환불이나 문의가 갈 곳도 카카오페이다.
         */
        return paymentService.pay(userId, reservationId,
                new PaymentRequest("EASY_PAY", tid, "KAKAO_PAY"),
                ACQUIRER, tid);
    }

    /** 결제 도중 돌아 나온 경우다. 남은 기록을 지워 다음 결제가 헌 거래번호를 쓰지 않게 한다. */
    public void cancel(Long userId) {
        forget(userId);
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new BusinessException(ErrorCode.KAKAO_PAY_NOT_CONFIGURED);
    }

    private TicketReservationDTO requireOwnReservation(Long userId, Long reservationId) {
        TicketReservationDTO reservation = paymentDAO.findReservation(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND));
        if (!reservation.getUserId().equals(userId)) {
            /* 남의 예약이라는 사실조차 알리지 않는다. 없는 것과 같게 답한다. */
            throw new BusinessException(ErrorCode.TICKET_RESERVATION_NOT_FOUND);
        }
        return reservation;
    }

    /**
     * 승인된 금액이 예약 금액과 같은지 본다.
     *
     * <p>카카오가 결제창을 띄울 때 받은 금액대로 승인하므로 어긋날 일이 드물지만, 어긋났다면
     * 그대로 발권해서는 안 된다. 트랜잭션 안이라 여기서 던지면 결제 기록도 남지 않는다.
     */
    private void requireSameAmount(TicketReservationDTO reservation, Map<String, Object> approved) {
        Object amount = approved.get("amount");
        if (!(amount instanceof Map<?, ?> detail)) return;

        Object total = detail.get("total");
        if (total == null) return;

        BigDecimal paid = new BigDecimal(String.valueOf(total));
        if (reservation.getTotalAmount() == null
                || reservation.getTotalAmount().compareTo(paid) != 0) {
            log.warn("카카오페이 승인 금액이 예약 금액과 다릅니다: reservation={} expected={} paid={}",
                    reservation.getReservationId(), reservation.getTotalAmount(), paid);
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }
    }

    private Map<String, Object> call(String url, Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    /* 카카오는 Bearer가 아니라 SECRET_KEY로 시작하는 형식을 쓴다. */
                    .header(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) throw new BusinessException(ErrorCode.KAKAO_PAY_CALL_FAILED);
            return response;
        } catch (RestClientResponseException exception) {
            /*
             * 카카오가 준 이유를 로그에 남긴다. 응답 본문 없이는 키가 틀린 것인지 금액이
             * 어긋난 것인지 알 수 없다. 손님에게는 그대로 전하지 않는다.
             */
            log.warn("카카오페이 호출 실패: url={} status={} body={}",
                    url, exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.KAKAO_PAY_CALL_FAILED);
        }
    }

    private void remember(Long userId, String tid, String orderId, Long reservationId) {
        /*
         * JSON으로 두지 않는다. 값이 셋뿐이고 어느 칸에도 구분자가 들어갈 수 없다 —
         * 거래번호와 주문번호는 우리가 만든 형식이고 예약 번호는 숫자다.
         */
        redisTemplate.opsForValue().set(sessionKey(userId),
                String.join("|", tid, orderId, String.valueOf(reservationId)), SESSION_TTL);
    }

    private String[] recall(Long userId) {
        String saved = redisTemplate.opsForValue().get(sessionKey(userId));
        if (saved == null) throw new BusinessException(ErrorCode.KAKAO_PAY_SESSION_EXPIRED);

        String[] parts = saved.split("\\|");
        if (parts.length != 3) throw new BusinessException(ErrorCode.KAKAO_PAY_SESSION_EXPIRED);
        return parts;
    }

    private void forget(Long userId) {
        redisTemplate.delete(sessionKey(userId));
    }

    private String sessionKey(Long userId) {
        return SESSION_PREFIX + userId;
    }

    private int amountOf(TicketReservationDTO reservation) {
        if (reservation.getTotalAmount() == null) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_REQUEST);
        }
        return reservation.getTotalAmount().intValueExact();
    }

    /** 카카오페이 결제창에 뜨는 상품명. 비어 있으면 손님이 무엇을 결제하는지 알 수 없다. */
    private String itemNameOf(TicketReservationDTO reservation) {
        String name = reservation.getProductName();
        return name == null || name.isBlank() ? "티켓 예약" : name;
    }

    /** 주문번호. 토스와 같은 형식을 쓴다 — 기록을 함께 볼 때 눈이 두 번 익지 않아도 된다. */
    static String orderIdOf(Long reservationId) {
        return "AMT-" + reservationId + "-" + java.util.UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
    }
}
