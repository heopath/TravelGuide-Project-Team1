package org.example.all_my_trip_project.domain.favorite.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.favorite.dao.FavoriteDAO;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteResult;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {
    private static final int MAX_PAGE_SIZE = 100;

    private final FavoriteDAO favoriteDAO;
    private final PlaceDAO placeDAO;

    @Transactional
    public FavoriteResult add(Long userId, Long placeId, String memo) {
        validateIds(userId, placeId);
        placeDAO.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "즐겨찾기할 장소를 찾을 수 없습니다. placeId=" + placeId));

        favoriteDAO.insert(userId, placeId, normalizeMemo(memo));
        return favoriteDAO.find(userId, placeId)
                .orElseThrow(() -> new IllegalStateException("즐겨찾기 저장 결과를 찾을 수 없습니다."));
    }

    public List<FavoriteResult> getFavorites(Long userId, int page, int size) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("userId는 1 이상이어야 합니다.");
        }
        int offset = calculateOffset(page, size);
        return favoriteDAO.findByUserId(userId, offset, size);
    }

    @Transactional
    public void remove(Long userId, Long placeId) {
        validateIds(userId, placeId);
        favoriteDAO.delete(userId, placeId);
    }

    private void validateIds(Long userId, Long placeId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("userId는 1 이상이어야 합니다.");
        }
        if (placeId == null || placeId < 1) {
            throw new IllegalArgumentException("placeId는 1 이상이어야 합니다.");
        }
    }

    private int calculateOffset(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1 이상 100 이하여야 합니다.");
        }
        try {
            return Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page와 size가 너무 큽니다.", exception);
        }
    }

    private String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        String normalized = memo.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("memo는 500자 이하여야 합니다.");
        }
        return normalized;
    }
}
