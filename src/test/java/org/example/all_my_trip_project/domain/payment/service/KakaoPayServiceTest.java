package org.example.all_my_trip_project.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 키가 없을 때 카카오를 부르지 않는지, 주문번호가 예약을 가리키는지만 본다. (#281)
 *
 * <p>ready·approve는 카카오 서버가 있어야 해서 여기서 확인하지 않는다. 그쪽 판단
 * (금액 대조·소유자 확인·거래번호 보관)은 화면 수용 기준이 소스를 보고 확인한다.
 */
class KakaoPayServiceTest {

    private KakaoPayService service(String secretKey) {
        return new KakaoPayService(
                mock(PaymentService.class),
                mock(org.example.all_my_trip_project.domain.payment.dao.PaymentDAO.class),
                mock(StringRedisTemplate.class),
                RestClient.builder(),
                secretKey,
                "TC0ONETIME",
                "http://localhost:8080");
    }

    @Test
    @DisplayName("시크릿 키가 없으면 꺼진 것으로 본다")
    void 시크릿_키가_없으면_꺼진_것으로_본다() {
        /* 화면이 미리 알아야 결제수단 목록에서 빼고 모의 결제만 남긴다. */
        assertThat(service("").isConfigured()).isFalse();
        assertThat(service("   ").isConfigured()).isFalse();
        assertThat(service(null).isConfigured()).isFalse();
        assertThat(service("DEV_SECRET_KEY_xxx").isConfigured()).isTrue();
    }

    @Test
    @DisplayName("주문번호는 토스와 같은 형식으로 예약을 가리킨다")
    void 주문번호는_토스와_같은_형식으로_예약을_가리킨다() {
        String orderId = KakaoPayService.orderIdOf(7L);

        /* 기록을 함께 볼 때 눈이 두 번 익지 않아도 되도록 형식을 맞춘다. */
        assertThat(orderId).startsWith("AMT-7-");
        assertThat(TossPaymentService.reservationIdOf(orderId)).isEqualTo(7L);
    }

    @Test
    @DisplayName("주문번호는 결제마다 다르다")
    void 주문번호는_결제마다_다르다() {
        /* 같으면 카카오 쪽에서 앞의 주문과 겹쳐 거절된다. */
        assertThat(KakaoPayService.orderIdOf(7L)).isNotEqualTo(KakaoPayService.orderIdOf(7L));
    }
}
