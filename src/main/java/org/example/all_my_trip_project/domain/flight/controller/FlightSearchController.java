package org.example.all_my_trip_project.domain.flight.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchResponse;
import org.example.all_my_trip_project.domain.flight.service.FlightSearchService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
public class FlightSearchController {

    private final FlightSearchService flightSearchService;

    /**
     * 운임 비교는 로그인 없이도 볼 수 있어야 한다.
     * 로그인이 필요한 시점은 선택 결과를 여행에 저장할 때다.
     *
     * <p>{@code firstPlanStartAt}/{@code lastPlanEndAt}은 일정 충돌 배지와 추천 스코어의 기준이다.
     * 가는 편에는 앞의 값만, 오는 편에는 뒤의 값만 넘긴다.
     */
    @GetMapping("/search")
    public ApiResponse<FlightSearchResponse> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int adults,
            @RequestParam(defaultValue = "true") boolean nonStop,
            @RequestParam(defaultValue = FlightSearchQuery.DEFAULT_CURRENCY) String currency,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime firstPlanStartAt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastPlanEndAt) {

        FlightSearchQuery query = new FlightSearchQuery(
                normalize(origin), normalize(destination), date, adults, nonStop, currency,
                firstPlanStartAt, lastPlanEndAt);
        return ApiResponse.success(flightSearchService.search(query));
    }

    private String normalize(String iataCode) {
        return iataCode == null ? null : iataCode.trim().toUpperCase(Locale.ROOT);
    }
}
