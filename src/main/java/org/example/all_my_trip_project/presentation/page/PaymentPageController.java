package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaymentPageController {

    /**
     * QR을 스캔한 폰이 여는 결제 승인 화면. (#281)
     *
     * <p>토큰을 서버에서 읽지 않고 화면 스크립트가 주소에서 꺼내 쓴다. 모델에 실어 주면
     * 토큰이 HTML 본문에 그대로 박혀, 화면을 캡처하거나 저장한 파일에 결제 권한이 남는다.
     *
     * <p>로그인이 필요한 화면이다({@code SecurityConfig}). 승인은 QR을 띄운 본인만 할 수
     * 있어야 하는데, 그러려면 누가 눌렀는지를 알아야 한다.
     */
    @GetMapping("/pay/qr")
    public String qrApprove() {
        return "payment/qr-approve";
    }
}
