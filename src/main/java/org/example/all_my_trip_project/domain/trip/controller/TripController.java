package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.global.security.SessionUserResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;
    private final SessionUserResolver sessionUserResolver;

    @PostMapping
    public ResponseEntity<TripDTO> create(@RequestBody TripDTO trip, HttpServletRequest request) {
        trip.setUserId(sessionUserResolver.requiredUserId(request));
        Long id = tripService.create(trip);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + id)).body(tripService.get(id));
    }

    @GetMapping("/{tripId}")
    public TripDTO get(@PathVariable Long tripId) {
        return tripService.get(tripId);
    }

    @GetMapping
    public List<TripDTO> getByUser(HttpServletRequest request) {
        return tripService.getByUser(sessionUserResolver.requiredUserId(request));
    }

    @PutMapping("/{tripId}")
    public TripDTO update(@PathVariable Long tripId, @RequestBody TripDTO trip) {
        trip.setTripId(tripId);
        tripService.update(trip);
        return tripService.get(tripId);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripId) {
        tripService.delete(tripId);
        return ResponseEntity.noContent().build();
    }
}
