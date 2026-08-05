package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;

@Controller
public class TripPageController {

    @Value("${kakao.maps.javascript-key:}")
    private String kakaoJavascriptKey;

    @GetMapping("/trips/new/basic")
    public String newTripBasic() {
        return "trips/basic";
    }

    @GetMapping("/trips/new/style")
    public String newTripStyle() {
        return "trips/style";
    }
@GetMapping("/trips/schedule")
    public String tripSchedule(Model model) {
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
        return "trips/schedule";
    }

    @GetMapping("/trips/{tripId}/schedule")
    public String tripSchedule(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
        return "trips/schedule";
    }

    @GetMapping("/trips/{tripSlug}/map")
    public String tripMap(@PathVariable String tripSlug) {
        return "trips/map";
    }

    @GetMapping("/trips/{tripSlug}/optimize")
    public String tripOptimize(@PathVariable String tripSlug) {
        return "trips/optimize";
    }
}
