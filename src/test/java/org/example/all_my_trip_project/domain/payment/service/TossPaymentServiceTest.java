package org.example.all_my_trip_project.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 승인에서 <b>어느 예약을 얼마에</b> 결제하는지를 정하는 두 판단만 본다. (#281)
 *
 * <p>이 둘이 틀리면 남의 예약에 결제가 붙거나, 싸게 결제하고 비싼 티켓을 받는다.
 * 승인 호출 자체는 토스 서버가 있어야 해서 여기서 확인하지 않는다.
 */
class TossPaymentServiceTest {

    @Test
    @DisplayName("주문번호에서 예약 번호를 꺼낸다")
    void 주문번호에서_예약번호를_꺼낸다() {
        assertThat(TossPaymentService.reservationIdOf("AMT-7-a1b2c3")).isEqualTo(7L);
    }

    @Test
    @DisplayName("우리가 만들지 않은 주문번호는 받지 않는다")
    void 우리가_만들지_않은_주문번호는_받지_않는다() {
        /* 화면이 보낸 값을 그대로 믿으면 아무 문자열로 남의 예약을 가리킬 수 있다. */
        assertThat(TossPaymentService.reservationIdOf("ORDER-7-x")).isNull();
        assertThat(TossPaymentService.reservationIdOf("AMT-abc-x")).isNull();
        assertThat(TossPaymentService.reservationIdOf("AMT-7")).isNull();
        assertThat(TossPaymentService.reservationIdOf(null)).isNull();
    }

    @Test
    @DisplayName("승인 금액이 예약 금액과 같아야 한다")
    void 승인_금액이_예약_금액과_같아야_한다() {
        /* 40000과 40000.00은 같은 돈이다. equals로 보면 소수 자리 때문에 다르다고 나온다. */
        assertThat(TossPaymentService.sameAmount(new BigDecimal("40000.00"), 40_000L)).isTrue();
        assertThat(TossPaymentService.sameAmount(new BigDecimal("40000"), 1_000L)).isFalse();
        assertThat(TossPaymentService.sameAmount(null, 40_000L)).isFalse();
        assertThat(TossPaymentService.sameAmount(new BigDecimal("40000"), null)).isFalse();
    }
}
