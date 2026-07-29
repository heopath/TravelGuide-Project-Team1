package org.example.all_my_trip_project.domain.favorite.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteResult;
import org.example.all_my_trip_project.domain.favorite.service.FavoriteService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PostMapping
    public FavoriteResult add(@RequestParam Long userId,
                              @RequestParam Long placeId,
                              @RequestParam(required = false) String memo) {
        return favoriteService.add(userId, placeId, memo);
    }

    @GetMapping
    public List<FavoriteResult> list(@RequestParam Long userId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return favoriteService.getFavorites(userId, page, size);
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@RequestParam Long userId,
                                       @RequestParam Long placeId) {
        favoriteService.remove(userId, placeId);
        return ResponseEntity.noContent().build();
    }
}
