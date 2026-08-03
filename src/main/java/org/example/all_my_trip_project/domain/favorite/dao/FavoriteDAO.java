package org.example.all_my_trip_project.domain.favorite.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteResult;
import org.example.all_my_trip_project.domain.favorite.mapper.FavoriteMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class FavoriteDAO {
    private final FavoriteMapper favoriteMapper;

    public int insert(Long userId, Long placeId, String memo) {
        return favoriteMapper.insert(userId, placeId, memo);
    }

    public Optional<FavoriteResult> find(Long userId, Long placeId) {
        return favoriteMapper.find(userId, placeId);
    }

    public List<FavoriteResult> findByUserId(Long userId, int offset, int size) {
        return favoriteMapper.findByUserId(userId, offset, size);
    }

    public long countByUserId(Long userId) {
        return favoriteMapper.countByUserId(userId);
    }

    public int delete(Long userId, Long placeId) {
        return favoriteMapper.delete(userId, placeId);
    }
}
