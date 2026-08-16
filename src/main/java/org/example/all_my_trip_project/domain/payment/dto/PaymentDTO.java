package org.example.all_my_trip_project.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 모의 결제 한 건.
 *
 * <p>{@code provider}는 항상 {@code MOCK}이다. 실제 PG를 붙이면 그때 값이 갈린다.
 * {@code providerPaymentKey}는 그 자리를 비워 두기 위해 남긴다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDTO {
    private Long paymentId;
    private Long reservationId;
    private String idempotencyKey;
    private String provider;
    private String providerPaymentKey;
    private String method;
    private String status;
    private BigDecimal amount;
    private String currencyCode;
    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
}
