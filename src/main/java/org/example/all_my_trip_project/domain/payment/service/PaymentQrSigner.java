package org.example.all_my_trip_project.domain.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * QR 결제 토큰을 만들고 검사한다. (#281)
 *
 * <p><b>서명만 하고 저장하지 않는다.</b> 토큰 안에 예약·사용자·만료 시각이 들어 있고 서명이
 * 붙어 있어, 서버는 받은 값이 자기가 만든 것인지만 확인하면 된다. 이렇게 한 이유는 QR이
 * 5분만 살기 때문이다. 5분짜리 값을 위해 테이블을 만들면 마이그레이션이 늘고(운영은 손으로
 * 넣는다) 만료된 행을 치우는 일까지 생긴다.
 *
 * <p>QR에 담을 수 있는 크기가 작다는 점도 이유다. 저장소를 쓰면 짧은 코드를 쓸 수 있지만,
 * 지금 QR 생성기(<code>core/qr-encoder.js</code>)가 최대 106바이트라 주소까지 담으려면
 * 토큰이 짧아야 한다. 그래서 서명을 12바이트로 자른다 — 96비트라 추측할 수 없다.
 */
@Slf4j
@Component
public class PaymentQrSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 12;
    private static final int PAYLOAD_FIELDS = 3;

    private final SecretKeySpec key;

    /**
     * 서명 열쇠.
     *
     * <p>설정이 없으면 뜰 때마다 새로 만든다. 앱을 재시작하면 그 전에 띄운 QR이 통하지
     * 않게 되는데, 세션도 함께 끊기므로(메모리 세션) 어차피 다시 로그인해야 한다.
     * 서버를 여러 대로 늘리면 그때는 같은 값을 넣어 줘야 한다 — 대수가 다르면 A가 만든
     * QR을 B가 못 알아본다.
     */
    public PaymentQrSigner(@Value("${payment.qr.secret:}") String secret) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.info("QR 결제 서명 열쇠를 이번 기동분으로 만들었습니다. "
                    + "여러 대로 운영하려면 payment.qr.secret을 설정하세요.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    /** 토큰 한 건. 서명 부분은 멱등키로도 쓰인다 — 같은 QR을 두 번 승인해도 한 번만 결제된다. */
    public record Token(Long reservationId, Long userId, OffsetDateTime expiresAt, String signature) {}

    public String sign(Long reservationId, Long userId, OffsetDateTime expiresAt) {
        String payload = reservationId + "." + userId + "." + expiresAt.toEpochSecond();
        return encode(payload) + "." + signatureOf(payload);
    }

    /**
     * 토큰을 풀어 본다. 서명이 다르거나 모양이 어긋나면 비어 있는 값을 돌려준다.
     *
     * <p>만료는 여기서 보지 않는다. 만료된 토큰과 위조된 토큰은 손님에게 다른 말을 해 줘야
     * 한다 — 하나는 "다시 띄우세요"이고 다른 하나는 "이 QR은 우리 것이 아닙니다"이다.
     */
    public Optional<Token> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 2) return Optional.empty();

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        String expected = signatureOf(payload);
        /*
         * 길이가 같은 두 문자열을 한 글자씩 끝까지 비교한다. equals는 다른 글자를 만나면
         * 바로 끝나서, 걸린 시간으로 앞자리가 맞았는지 알 수 있다.
         */
        if (!java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        String[] fields = payload.split("\\.");
        if (fields.length != PAYLOAD_FIELDS) return Optional.empty();
        try {
            return Optional.of(new Token(
                    Long.parseLong(fields[0]),
                    Long.parseLong(fields[1]),
                    OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochSecond(Long.parseLong(fields[2])),
                            java.time.ZoneId.systemDefault()),
                    parts[1]));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String signatureOf(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(full, SIGNATURE_BYTES));
        } catch (GeneralSecurityException exception) {
            /* HmacSHA256은 표준 구현에 반드시 있다. 여기 오면 JVM 문제다. */
            throw new IllegalStateException("QR 결제 토큰에 서명할 수 없습니다.", exception);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
