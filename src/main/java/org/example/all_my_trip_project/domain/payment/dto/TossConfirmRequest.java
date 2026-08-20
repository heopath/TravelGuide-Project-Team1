package org.example.all_my_trip_project.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 토스 결제창에서 돌아올 때 실려 오는 값. 그대로 승인 요청에 쓴다.
 *
 * <p>어느 예약인지는 여기에 없다. {@code orderId}에서 꺼낸다 — 주문번호는 결제창을 띄울 때
 * 토스에 함께 넘어가 그 결제에 묶이므로, 화면이 다른 예약을 끼워 넣을 수 없다.
 */
public record TossConfirmRequest(
        @NotBlank
        @Size(max = 200)
        String paymentKey,

        @NotBlank
        @Size(max = 64)
        String orderId,

        @NotNull
        @Positive
        Long amount
) {}
