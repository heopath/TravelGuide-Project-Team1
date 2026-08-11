package org.example.all_my_trip_project.domain.accommodation.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationSearchService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/accommodations")
@RequiredArgsConstructor
public class AccommodationSearchController {

    /** 실수로 큰 값이 들어와도 외부 API를 오래 붙들지 않게 막는다. */
    private static final int MAX_NIGHTS = 30;

    private final AccommodationSearchService accommodationSearchService;

    /**
     * 숙소 비교는 로그인 없이도 볼 수 있어야 한다.
     * 로그인이 필요한 시점은 선택 결과를 여행에 저장할 때다. 항공과 같은 기준이다.
     */
    @GetMapping("/search")
    public ApiResponse<AccommodationSearchResponse> search(
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "2") int adults,
            @RequestParam(defaultValue = "1") int rooms,
            @RequestParam(defaultValue = AccommodationSearchQuery.DEFAULT_CURRENCY) String currency) {

        validate(destination, checkIn, checkOut);

        AccommodationSearchQuery query = new AccommodationSearchQuery(
                destination.trim(), checkIn, checkOut, adults, rooms, currency);
        return ApiResponse.success(accommodationSearchService.search(query));
    }

    /**
     * 날짜 검증을 컨트롤러에 둔다.
     *
     * <p>{@code AccommodationSearchQuery}의 compact 생성자에서 막지 않는 이유는,
     * 거기서 던지면 캐시 키를 만들기도 전에 예외가 나 원인을 로그로 추적하기 어렵기 때문이다.
     * 잘못된 요청은 들어오는 자리에서 걸러 400으로 돌려준다.
     */
    private void validate(String destination, LocalDate checkIn, LocalDate checkOut) {
        if (destination == null || destination.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ACCOMMODATION_DESTINATION);
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessException(ErrorCode.INVALID_ACCOMMODATION_PERIOD);
        }
        if (checkIn.plusDays(MAX_NIGHTS).isBefore(checkOut)) {
            throw new BusinessException(ErrorCode.INVALID_ACCOMMODATION_PERIOD);
        }
    }
}
