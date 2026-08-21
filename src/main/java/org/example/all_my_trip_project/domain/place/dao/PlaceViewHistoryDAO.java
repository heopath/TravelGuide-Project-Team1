package org.example.all_my_trip_project.domain.place.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.RecentPlaceResult;
import org.example.all_my_trip_project.domain.place.mapper.PlaceViewHistoryMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class PlaceViewHistoryDAO {
    private final PlaceViewHistoryMapper mapper;

    public int record(Long userId, Long placeId) { return mapper.record(userId, placeId); }
    public List<RecentPlaceResult> findRecent(Long userId, int size) {
        return mapper.findRecent(userId, size);
    }
    public int deleteBeyondLimit(Long userId, int limit) {
        return mapper.deleteBeyondLimit(userId, limit);
    }
    public int deleteByUserId(Long userId) { return mapper.deleteByUserId(userId); }
}
