package org.example.all_my_trip_project.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.service.ItineraryItemService;
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
@RequestMapping("/api/trip-days/{tripDayId}/items")
@RequiredArgsConstructor
public class ItineraryItemController {
    private final ItineraryItemService itineraryItemService;

    @PostMapping
    public ResponseEntity<ItineraryItemDTO> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable Long tripDayId,
                                                   @RequestBody ItineraryItemDTO item) {
        item.setTripDayId(tripDayId);
        Long id = itineraryItemService.create(requireUserId(principal), item);
        return ResponseEntity.created(URI.create("/api/trip-days/" + tripDayId + "/items/" + id)).body(item);
    }

    @GetMapping
    public List<ItineraryItemDTO> getByTripDay(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable Long tripDayId) {
        return itineraryItemService.getByTripDay(requireUserId(principal), tripDayId);
    }

    @PutMapping("/{itemId}")
    public ItineraryItemDTO update(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable Long tripDayId, @PathVariable Long itemId,
                                   @RequestBody ItineraryItemDTO item) {
        item.setTripDayId(tripDayId);
        item.setItineraryItemId(itemId);
        itineraryItemService.update(requireUserId(principal), item);
        return item;
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable Long tripDayId, @PathVariable Long itemId) {
        itineraryItemService.delete(requireUserId(principal), tripDayId, itemId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
