package org.example.all_my_trip_project.domain.payment.service;

import org.example.all_my_trip_project.domain.payment.dao.PaymentDAO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentResultResponse;
import org.example.all_my_trip_project.domain.payment.dto.TossConfirmRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이미 끝난 결제로 다시 들어왔을 때 결제사를 부르지 않는지 본다. (#281)
 *
 * <p>돌아오는 주소를 새로고침하면 벌어지는 일이다. 결제사는 승인된 결제를 다시 승인해 주지
 * 않으므로, 그 거절을 그대로 손님에게 보이면 결제가 끝나고 티켓까지 나온 상태에서
 * <b>"결제 실패"</b>라고 말하게 된다. 실제로 그 증상이 있었다.
 *
 * <p>결제사를 부르지 않았다는 것은 돌려받은 값으로 안다. 불렀다면 테스트 환경에서는 바깥으로
 * 나가지 못해 예외가 났을 것이다.
 */
class PaymentReplayTest {

    private static final PaymentResultResponse RECORDED =
            new PaymentResultResponse(null, null, List.of(), true);

    private TicketReservationDTO reservation() {
        return TicketReservationDTO.builder()
                .reservationId(42L)
                .userId(7L)
                .totalAmount(new BigDecimal("20000"))
                .productName("제주 아쿠아리움 입장권")
                .quantity(2)
                .build();
    }

    @Test
    @DisplayName("토스: 이미 기록된 결제면 승인을 다시 부르지 않는다")
    void 토스_이미_기록된_결제면_승인을_다시_부르지_않는다() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentDAO paymentDAO = mock(PaymentDAO.class);
        given(paymentDAO.findReservation(42L)).willReturn(Optional.of(reservation()));
        given(paymentService.findRecorded(7L, "pk-1", 42L)).willReturn(RECORDED);

        TossPaymentService service = new TossPaymentService(
                paymentService, paymentDAO, RestClient.builder(), "test_gsk_x");

        PaymentResultResponse result = service.confirm(7L,
                new TossConfirmRequest("pk-1", "AMT-42-abc123", 20_000L));

        assertThat(result).isSameAs(RECORDED);
        /* 결제를 새로 만들지도 않는다. 만들면 멱등키가 겹쳐 DB가 거절한다. */
        verify(paymentService, never()).pay(anyLong(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("카카오: 이미 기록된 결제면 승인을 다시 부르지 않는다")
    void 카카오_이미_기록된_결제면_승인을_다시_부르지_않는다() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentDAO paymentDAO = mock(PaymentDAO.class);
        given(paymentDAO.findReservation(42L)).willReturn(Optional.of(reservation()));
        given(paymentService.findRecorded(7L, "tid-1", 42L)).willReturn(RECORDED);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(values);
        /* 결제를 시작할 때 서버가 적어 둔 기록. 승인 뒤에도 지우지 않아 새로고침이 찾아낸다. */
        given(values.get("all-my-trips:payment:kakao:7")).willReturn("tid-1|AMT-42-abc123|42");

        KakaoPayService service = new KakaoPayService(
                paymentService, paymentDAO, redis, RestClient.builder(),
                "DEV_SECRET_KEY_x", "TC0ONETIME", "http://localhost:8080");

        assertThat(service.approve(7L, "pg-token")).isSameAs(RECORDED);
        verify(paymentService, never()).pay(anyLong(), anyLong(), any(), anyString(), anyString());
        /* 기록을 지우면 그다음 새로고침이 거래번호를 잃어 같은 증상으로 돌아간다. */
        verify(redis, never()).delete(eq("all-my-trips:payment:kakao:7"));
    }
}
