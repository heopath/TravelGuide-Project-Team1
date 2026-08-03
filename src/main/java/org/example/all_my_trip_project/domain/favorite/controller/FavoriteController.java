package org.example.all_my_trip_project.domain.favorite.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteResult;
import org.example.all_my_trip_project.domain.favorite.service.FavoriteService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public FavoriteResult add(@AuthenticationPrincipal AuthenticatedUser principal,
                              @RequestParam Long placeId,
                              @RequestParam(required = false) String memo) {
        return favoriteService.add(requireUserId(principal), placeId, memo);
    }

    @GetMapping
    public List<FavoriteResult> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return favoriteService.getFavorites(requireUserId(principal), page, size);
    }

    @GetMapping("/count")
    public long count(@AuthenticationPrincipal AuthenticatedUser principal) {
        return favoriteService.countFavorites(requireUserId(principal));
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @RequestParam Long placeId) {
        favoriteService.remove(requireUserId(principal), placeId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
