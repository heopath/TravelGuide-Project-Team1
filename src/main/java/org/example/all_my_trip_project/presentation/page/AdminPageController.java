package org.example.all_my_trip_project.presentation.page;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @Value("${kakao.maps.javascript-key:}")
    private String kakaoJavascriptKey;

    @GetMapping("/admin")
    public String admin() {
        return "admin/admin";
    }

    /**
     * 장소 등록 화면에 카카오 키를 넘긴다.
     *
     * <p>지도를 그리려는 것이 아니라 <b>주소로 좌표를 찾는 데만</b> 쓴다. 좌표를 손으로
     * 알아내 옮겨 적게 두면 등록이 사실상 막히고, 좌표 없이 등록된 장소는 지도와
     * 동선 계산에서 조용히 빠진다.
     *
     * <p>키가 없으면 빈 문자열이라 템플릿이 SDK를 불러오지 않고 버튼도 숨겨진다.
     * 그래도 좌표를 직접 입력하면 등록은 그대로 된다.
     */
    @GetMapping("/admin/places")
    public String places(Model model) {
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
        return "admin/places";
    }

    /**
     * 현장 검표 화면. (#266)
     *
     * <p>관리자 대시보드 안의 검표 탭과 따로 둔다. 그쪽은 사이드바와 표가 있는 데스크톱
     * 배치라 입구에서 폰으로 쓸 수 없다. 하는 일도 다르다 — 대시보드는 수동 입력과 이력
     * 조회, 이 화면은 카메라 스캔이다.
     *
     * <p>검표 API는 그대로 쓴다. 권한도 {@code ApiSecurityConfig}가 이미 걸고 있다.
     */
    @GetMapping("/admin/scan")
    public String scan() {
        return "admin/scan";
    }
}
