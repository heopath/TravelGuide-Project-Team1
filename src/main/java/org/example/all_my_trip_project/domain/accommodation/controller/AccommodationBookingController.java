package org.example.all_my_trip_project.domain.accommodation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.dto.SaveAccommodationRequest;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationBookingService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행에 담아둔 숙소.
 *
 * <p>항공은 구간이 0/1로 고정이라 {@code /flights/{leg}}로 가리킬 수 있었지만,
 * 숙박은 건수가 가변이라 생성된 id로 식별한다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips/{tripId}/accommodations")
@RequiredArgsConstructor
public class AccommodationBookingController {

    private final AccommodationBookingService accommodationBookingService;

    /** 숙소 선택을 여행에 저장한다. 같은 기간을 다시 고르면 교체된다. */
    @PostMapping
    public ApiResponse<TripAccommodationsResponse> save(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @Valid @RequestBody SaveAccommodationRequest request) {
        return ApiResponse.success("숙소를 담았어요.",
                accommodationBookingService.save(requireUserId(principal), tripId, request));
    }

    @DeleteMapping("/{accommodationBookingId}")
    public ApiResponse<TripAccommodationsResponse> remove(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId,
            @PathVariable Long accommodationBookingId) {
        return ApiResponse.success("숙소를 뺐어요.",
                accommodationBookingService.remove(requireUserId(principal), tripId, accommodationBookingId));
    }

    @GetMapping
    public ApiResponse<TripAccommodationsResponse> getBookings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long tripId) {
        return ApiResponse.success(
                accommodationBookingService.getBookings(requireUserId(principal), tripId));
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
