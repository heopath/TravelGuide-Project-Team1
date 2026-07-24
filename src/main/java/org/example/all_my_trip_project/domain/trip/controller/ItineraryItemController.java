package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.service.ItineraryItemService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/trip-days/{tripDayId}/items")
@RequiredArgsConstructor
public class ItineraryItemController {
    private final ItineraryItemService itineraryItemService;

    @PostMapping
    public ResponseEntity<ItineraryItemDTO> create(@PathVariable Long tripDayId,
                                                   @RequestBody ItineraryItemDTO item) {
        item.setTripDayId(tripDayId);
        Long id = itineraryItemService.create(item);
        return ResponseEntity.created(URI.create("/api/trip-days/" + tripDayId + "/items/" + id)).body(item);
    }

    @GetMapping
    public List<ItineraryItemDTO> getByTripDay(@PathVariable Long tripDayId) {
        return itineraryItemService.getByTripDay(tripDayId);
    }

    @PutMapping("/{itemId}")
    public ItineraryItemDTO update(@PathVariable Long tripDayId, @PathVariable Long itemId,
                                   @RequestBody ItineraryItemDTO item) {
        item.setTripDayId(tripDayId);
        item.setItineraryItemId(itemId);
        itineraryItemService.update(item);
        return item;
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripDayId, @PathVariable Long itemId) {
        itineraryItemService.delete(itemId);
        return ResponseEntity.noContent().build();
    }
}
