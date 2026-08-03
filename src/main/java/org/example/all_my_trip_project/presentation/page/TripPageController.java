package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;

@Controller
public class TripPageController {

    @GetMapping("/trips/new/basic")
    public String newTripBasic() {
        return "trips/basic";
    }

    @GetMapping("/trips/new/style")
    public String newTripStyle() {
        return "trips/style";
    }

    @GetMapping("/trips/recommendations")
    public String tripRecommendations() {
        return "trips/recommendations";
    }

    @GetMapping("/trips/schedule")
    public String tripSchedule() {
        return "trips/schedule";
    }

    @GetMapping("/trips/{tripId}/schedule")
    public String tripSchedule(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
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
