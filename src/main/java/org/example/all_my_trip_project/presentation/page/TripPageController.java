package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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

    @GetMapping("/trips/{tripSlug}/schedule")
    public String tripSchedule(@PathVariable String tripSlug) {
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
