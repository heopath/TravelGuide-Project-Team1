package org.example.all_my_trip_project.domain.weather.dto;

public record WeatherResponse(
        String visitDate,
        String weatherType,
        String icon,
        String temperature,
        String rainPercent,
        String recommendation,
        String message
) {
}
