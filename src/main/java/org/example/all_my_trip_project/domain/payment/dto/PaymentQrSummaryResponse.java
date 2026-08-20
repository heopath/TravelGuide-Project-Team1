package org.example.all_my_trip_project.domain.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * QR을 스캔한 기기에 보여줄 결제 내용. (#281)
 *
 * <p>승인 버튼을 누르기 전에 무엇을 얼마에 결제하는지 보여야 한다. 금액을 확인하지 않고
 * 누르게 만드는 결제 화면은 없다.
 */
public record PaymentQrSummaryResponse(
        Long reservationId,
        String reservationNumber,
        String productName,
        String optionName,
        Integer quantity,
        BigDecimal amount,
        String currency,
        /** 이미 결제가 끝난 예약인지. 스캔이 늦었을 때 화면이 다른 말을 해야 한다. */
        boolean alreadyPaid,
        /** 어느 간편결제 창에서 띄운 QR인지. 스캔한 기기가 같은 사업자 모양으로 그린다. */
        String easyPayProvider,
        OffsetDateTime expiresAt,
        OffsetDateTime serverTime
) {}
