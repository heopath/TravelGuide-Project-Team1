package org.example.all_my_trip_project.domain.review.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewDTO;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRatingCount;
import org.example.all_my_trip_project.domain.review.mapper.PlaceReviewMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class PlaceReviewDAO {
    private final PlaceReviewMapper mapper;

    public int insert(PlaceReviewDTO review) { return mapper.insert(review); }
    public Optional<PlaceReviewDTO> findById(Long reviewId, Long requesterUserId) {
        return mapper.findById(reviewId, requesterUserId);
    }
    public Optional<PlaceReviewDTO> findByUserAndPlace(Long userId, Long placeId) {
        return mapper.findByUserAndPlace(userId, placeId);
    }
    public List<PlaceReviewDTO> findPage(Long placeId, Long requesterUserId, int offset, int limit) {
        return mapper.findPage(placeId, requesterUserId, offset, limit);
    }
    public List<PlaceReviewDTO> findMyPage(Long userId, int offset, int limit) {
        return mapper.findMyPage(userId, offset, limit);
    }
    public long countByUser(Long userId) { return mapper.countByUser(userId); }
    public long countVisible(Long placeId) { return mapper.countVisible(placeId); }
    public BigDecimal averageVisible(Long placeId) { return mapper.averageVisible(placeId); }
    public List<PlaceReviewRatingCount> countByRating(Long placeId) { return mapper.countByRating(placeId); }
    public boolean hasCompletedVisit(Long userId, Long placeId) {
        return mapper.hasCompletedVisit(userId, placeId);
    }
    public int update(PlaceReviewDTO review) { return mapper.update(review); }
    public int delete(Long reviewId, Long userId) { return mapper.delete(reviewId, userId); }
    public int updatePlaceAverageRating(Long placeId) { return mapper.updatePlaceAverageRating(placeId); }
}
