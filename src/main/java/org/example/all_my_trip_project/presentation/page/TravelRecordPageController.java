package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class TravelRecordPageController {

    @GetMapping("/trips/{tripId}/record")
    public String tripRecord(@PathVariable Long tripId, Model model) {
        model.addAttribute("tripId", tripId);
        return "trips/record";
    }
}
