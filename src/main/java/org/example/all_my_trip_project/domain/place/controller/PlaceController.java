package org.example.all_my_trip_project.domain.place.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {
    private final PlaceService placeService;

    @PostMapping
    public ResponseEntity<PlaceDTO> create(@RequestBody PlaceDTO place) {
        Long id = placeService.create(place);
        return ResponseEntity.created(URI.create("/api/places/" + id)).body(placeService.get(id));
    }

    @GetMapping("/{placeId}")
    public PlaceDTO get(@PathVariable Long placeId) {
        return placeService.get(placeId);
    }

    @GetMapping
    public List<PlaceDTO> search(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String category) {
        return placeService.search(keyword, category);
    }

    @PutMapping("/{placeId}")
    public PlaceDTO update(@PathVariable Long placeId, @RequestBody PlaceDTO place) {
        place.setPlaceId(placeId);
        placeService.update(place);
        return placeService.get(placeId);
    }

    @DeleteMapping("/{placeId}")
    public ResponseEntity<Void> delete(@PathVariable Long placeId) {
        placeService.delete(placeId);
        return ResponseEntity.noContent().build();
    }
}
