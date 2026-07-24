package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home() {
        return "home/home";
    }

    @GetMapping("/auth/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/auth/signup")
    public String signup() {
        return "auth/signup";
    }

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

    @GetMapping("/trips/{tripSlug}/record")
    public String tripRecord(@PathVariable String tripSlug) {
        return "trips/record";
    }

    @GetMapping("/guide")
    public String guide() {
        return "guide/guide";
    }

    @GetMapping("/guide/themes")
    public String guideThemes() {
        return "guide/themes";
    }

    @GetMapping("/guide/places/{placeSlug}")
    public String placeDetail(@PathVariable String placeSlug) {
        return "guide/place-detail";
    }

    @GetMapping("/ai-guide")
    public String aiGuide() {
        return "guide/ai-guide";
    }

    @GetMapping("/booking")
    public String booking() {
        return "booking/booking";
    }

    @GetMapping("/booking/tickets/{ticketSlug}")
    public String ticket(@PathVariable String ticketSlug) {
        return "booking/ticket";
    }

    @GetMapping("/booking/hotels")
    public String hotels() {
        return "booking/hotels";
    }

    @GetMapping("/booking/flights")
    public String flights() {
        return "booking/flights";
    }

    @GetMapping("/booking/queue")
    public String bookingQueue() {
        return "booking/queue";
    }

    @GetMapping("/mypage")
    public String mypage() {
        return "mypage/mypage";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin/admin";
    }
}
