package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.service.TripDayService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/trips/{tripId}/days")
@RequiredArgsConstructor
public class TripDayController {
    private final TripDayService tripDayService;

    @PostMapping
    public ResponseEntity<TripDayDTO> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable Long tripId,
                                             @RequestBody TripDayDTO tripDay) {
        tripDay.setTripId(tripId);
        Long id = tripDayService.create(requireUserId(principal), tripDay);
        return ResponseEntity.created(URI.create("/api/trips/" + tripId + "/days/" + id)).body(tripDay);
    }

    @GetMapping
    public List<TripDayDTO> getByTrip(@AuthenticationPrincipal AuthenticatedUser principal,
                                      @PathVariable Long tripId) {
        return tripDayService.getByTrip(requireUserId(principal), tripId);
    }

    @PutMapping("/{tripDayId}")
    public TripDayDTO update(@AuthenticationPrincipal AuthenticatedUser principal,
                             @PathVariable Long tripId, @PathVariable Long tripDayId,
                             @RequestBody TripDayDTO tripDay) {
        tripDay.setTripId(tripId);
        tripDay.setTripDayId(tripDayId);
        tripDayService.update(requireUserId(principal), tripDay);
        return tripDay;
    }

    @DeleteMapping("/{tripDayId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable Long tripId, @PathVariable Long tripDayId) {
        tripDayService.delete(requireUserId(principal), tripId, tripDayId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
