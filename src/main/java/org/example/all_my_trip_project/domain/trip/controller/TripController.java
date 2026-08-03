package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
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
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripDTO> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @RequestBody TripDTO trip) {
        Long userId = requireUserId(principal);
        trip.setUserId(userId);
        Long id = tripService.create(trip);
        return ResponseEntity.created(URI.create("/api/trips/" + id))
                .body(tripService.get(userId, id));
    }

    @GetMapping("/{tripId}")
    public TripDTO get(@AuthenticationPrincipal AuthenticatedUser principal,
                       @PathVariable Long tripId) {
        return tripService.get(requireUserId(principal), tripId);
    }

    @GetMapping
    public List<TripDTO> getByUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return tripService.getByUser(requireUserId(principal));
    }

    @PutMapping("/{tripId}")
    public TripDTO update(@AuthenticationPrincipal AuthenticatedUser principal,
                          @PathVariable Long tripId, @RequestBody TripDTO trip) {
        Long userId = requireUserId(principal);
        trip.setTripId(tripId);
        trip.setUserId(userId);
        tripService.update(userId, trip);
        return tripService.get(userId, tripId);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable Long tripId) {
        tripService.delete(requireUserId(principal), tripId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
