package org.example.all_my_trip_project.presentation.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AiPageController {

    @GetMapping("/ai-guide")
    public String aiGuide() {
        return "guide/ai-guide";
    }
}
