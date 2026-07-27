package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PlacePageController {

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
}
