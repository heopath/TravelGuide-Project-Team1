package org.example.all_my_trip_project.presentation.page;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.global.security.turnstile.TurnstileProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AuthPageController {

    private final TurnstileProperties turnstileProperties;

    @GetMapping("/auth/login")
    public String login(Model model) {
        addTurnstile(model);
        return "auth/login";
    }

    @GetMapping("/auth/signup")
    public String signup(Model model) {
        addTurnstile(model);
        return "auth/signup";
    }

    @GetMapping("/auth/forgot-password")
    public String forgotPassword() {
        return "auth/forgot-password";
    }

    // 토큰은 화면에서 읽어 쓰므로 여기서 검사하지 않는다. 유효한지는 화면이 열린 뒤
    // /api/v1/auth/password-reset?token=...으로 확인한다. 서버가 미리 걸러 400을 내면
    // 만료된 링크를 눌렀을 때 안내 대신 오류 화면이 뜬다.
    @GetMapping("/auth/reset-password")
    public String resetPassword() {
        return "auth/reset-password";
    }

    private void addTurnstile(Model model) {
        model.addAttribute("turnstileEnabled", turnstileProperties.isEnabled());
        model.addAttribute("turnstileSiteKey", turnstileProperties.getSiteKey());
    }
}
