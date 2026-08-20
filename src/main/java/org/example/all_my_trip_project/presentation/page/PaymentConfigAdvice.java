package org.example.all_my_trip_project.presentation.page;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 결제창이 쓰는 공개 키를 결제가 일어나는 화면에 실어 준다. (#281)
 *
 * <p>결제 경로가 두 곳이다 — 마이페이지 `내 티켓`과 예약 화면 `내 예약`. 각 컨트롤러가
 * 같은 값을 모델에 넣게 두면 화면이 늘 때 빠뜨린다. 실제로 카카오 지도 키가 그렇게
 * 컨트롤러 세 곳에 흩어져 있다.
 *
 * <p>클라이언트 키는 브라우저에 드러나도 되는 값이라 화면에 실어도 된다. <b>시크릿 키는
 * 절대 여기 두지 않는다</b> — 승인은 서버에서만 하고, 그 키는 서버 설정에만 있다.
 */
@ControllerAdvice(assignableTypes = {
        BookingPageController.class,
        MemberPageController.class
})
public class PaymentConfigAdvice {

    private final String tossClientKey;

    public PaymentConfigAdvice(@Value("${payment.toss.client-key:}") String tossClientKey) {
        this.tossClientKey = tossClientKey == null ? "" : tossClientKey.trim();
    }

    @ModelAttribute("tossClientKey")
    public String tossClientKey() {
        return tossClientKey;
    }
}
