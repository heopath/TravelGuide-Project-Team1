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

    private void addTurnstile(Model model) {
        model.addAttribute("turnstileEnabled", turnstileProperties.isEnabled());
        model.addAttribute("turnstileSiteKey", turnstileProperties.getSiteKey());
    }
}
