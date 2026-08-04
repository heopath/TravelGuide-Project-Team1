package org.example.all_my_trip_project.domain.weather.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.weather.dto.WeatherResponse;
import org.example.all_my_trip_project.domain.weather.service.WeatherService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherController {
    private final WeatherService weatherService;

    @GetMapping
    public ApiResponse<WeatherResponse> getWeather(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam String date,
            @RequestParam(required = false) String time) {
        return ApiResponse.success(weatherService.getWeather(latitude, longitude, date, time));
    }
}
