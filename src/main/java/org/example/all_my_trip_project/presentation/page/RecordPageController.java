package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class RecordPageController {

    @GetMapping("/trips/{tripSlug}/record")
    public String tripRecord(@PathVariable String tripSlug) {
        return "trips/record";
    }
}
