package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class BookingPageController {

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
}
