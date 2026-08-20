package org.example.all_my_trip_project.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentRequest(

        @NotBlank
        @Pattern(regexp = "CARD|TRANSFER|VIRTUAL_ACCOUNT|EASY_PAY",
                message = "지원하지 않는 결제 수단입니다.")
        String method,

        /**
         * 화면이 만들어 보내는 멱등키.
         *
         * <p>결제 버튼을 두 번 누르거나 응답이 유실되어 재시도할 때, 같은 키로 오면 결제를
         * 다시 만들지 않고 앞의 결과를 그대로 돌려주기 위한 값이다. {@code payments}의
         * {@code idempotency_key}가 UNIQUE라 DB가 마지막 방어선이 된다.
         */
        @NotBlank
        @Size(max = 100)
        String idempotencyKey,

        /**
         * 간편결제 사업자. {@code method}가 {@code EASY_PAY}일 때만 쓴다.
         *
         * <p>{@code method}를 늘리지 않고 칸을 따로 둔 이유는, 카카오페이든 토스든 결제
         * 수단으로는 똑같이 간편결제이기 때문이다. {@code payments.method}에는 CHECK 제약이
         * 걸려 있어 값을 늘리려면 마이그레이션이 필요하고, 사업자가 하나 늘 때마다 제약을
         * 고쳐야 한다. 사업자는 {@code provider} 칸에 적는다. (#281)
         *
         * <p>{@code QR_PAY}는 화면이 직접 보내지 않는다. QR 결제를 승인할 때
         * {@code PaymentQrService}가 붙인다.
         *
         * <p>비어 있으면 {@link jakarta.validation.constraints.Pattern}이 통과시키므로,
         * EASY_PAY인데 없는 경우는 서비스에서 거른다.
         */
        @Pattern(regexp = "KAKAO_PAY|TOSS_PAY|QR_PAY",
                message = "지원하지 않는 간편결제 사업자입니다.")
        String easyPayProvider
) {}
