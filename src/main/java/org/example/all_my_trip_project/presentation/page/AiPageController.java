package org.example.all_my_trip_project.presentation.page;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AiPageController {

    @Value("${ai.guide.mock.enabled:false}")
    private boolean mockEnabled;

    @GetMapping("/ai-guide")
    public String aiGuide(Model model) {
        model.addAttribute("aiMockEnabled", mockEnabled);
        return "guide/ai-guide";
    }
}
