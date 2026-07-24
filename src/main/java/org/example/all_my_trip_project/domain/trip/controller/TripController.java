package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<TripDTO> create(@RequestBody TripDTO trip) {
        Long id = tripService.create(trip);
        return ResponseEntity.created(URI.create("/api/trips/" + id)).body(tripService.get(id));
    }

    @GetMapping("/{tripId}")
    public TripDTO get(@PathVariable Long tripId) {
        return tripService.get(tripId);
    }

    @GetMapping
    public List<TripDTO> getByUser(@RequestParam Long userId) {
        return tripService.getByUser(userId);
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
