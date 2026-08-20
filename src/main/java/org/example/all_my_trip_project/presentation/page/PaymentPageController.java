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
    /**
     * 토스 결제창에서 돌아오는 화면. (#281)
     *
     * <p>성공과 실패가 같은 주소로 돌아온다. 실패에만 다른 주소를 주면 화면이 두 벌이 되는데,
     * 둘이 보여 줄 내용은 문구 한 줄 차이다. 어느 쪽인지는 주소에 실려 오는 값으로 가른다.
     *
     * <p>결제 결과는 모델에 싣지 않는다. 승인은 화면 스크립트가 서버 API로 요청한다 —
     * 여기서 승인해 버리면 새로고침만으로 승인이 다시 불린다.
     */
    @GetMapping("/pay/toss")
    public String tossReturn() {
        return "payment/toss-return";
    }

    /**
     * 카카오페이 결제창에서 돌아오는 화면. (#281)
     *
     * <p>성공·취소·실패가 모두 이 주소로 돌아온다. 카카오는 성공일 때만 {@code pg_token}을
     * 붙여 주므로, 그 값이 있는지로 가른다. 취소·실패는 우리가 미리 붙여 둔 {@code result}로
     * 어느 쪽인지 구분한다.
     */
    @GetMapping("/pay/kakao")
    public String kakaoReturn() {
        return "payment/kakao-return";
    }

    @GetMapping("/pay/qr")
    public String qrApprove() {
        return "payment/qr-approve";
    }
}
