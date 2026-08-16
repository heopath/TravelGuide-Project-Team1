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
        String idempotencyKey
) {}
