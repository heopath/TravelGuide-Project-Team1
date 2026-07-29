package org.example.all_my_trip_project.presentation.page;

import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;

@Configuration
public class ErrorPageController {

    @Bean
    ErrorViewResolver allMyTripsErrorViewResolver() {
        return (request, status, model) -> {
            var errorModel = new LinkedHashMap<>(model);
            errorModel.put("status", status.value());
            return new ModelAndView("error/error", errorModel, status);
        };
    }
}

