package org.example.all_my_trip_project.presentation.page;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PlacePageController {

    @Value("${kakao.maps.javascript-key:}")
    private String kakaoJavascriptKey;

    @GetMapping("/guide")
    public String guide() {
        return "guide/guide";
    }

    @GetMapping("/guide/themes")
    public String guideThemes() {
        return "guide/themes";
    }

    @GetMapping("/guide/places/{placeSlug}")
    public String placeDetail(@PathVariable String placeSlug, Model model) {
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
        return "guide/place-detail";
    }
}
