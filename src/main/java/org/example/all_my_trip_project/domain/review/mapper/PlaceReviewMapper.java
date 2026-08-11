package org.example.all_my_trip_project.domain.review.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewDTO;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRatingCount;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PlaceReviewMapper {
    int insert(PlaceReviewDTO review);
    Optional<PlaceReviewDTO> findById(@Param("reviewId") Long reviewId,
                                      @Param("requesterUserId") Long requesterUserId);
    Optional<PlaceReviewDTO> findByUserAndPlace(@Param("userId") Long userId,
                                                @Param("placeId") Long placeId);
    List<PlaceReviewDTO> findPage(@Param("placeId") Long placeId,
                                  @Param("requesterUserId") Long requesterUserId,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);
    List<PlaceReviewDTO> findMyPage(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);
    long countByUser(@Param("userId") Long userId);
    long countVisible(@Param("placeId") Long placeId);
    BigDecimal averageVisible(@Param("placeId") Long placeId);
    List<PlaceReviewRatingCount> countByRating(@Param("placeId") Long placeId);
    boolean hasCompletedVisit(@Param("userId") Long userId,
                              @Param("placeId") Long placeId);
    int update(PlaceReviewDTO review);
    int delete(@Param("reviewId") Long reviewId,
               @Param("userId") Long userId);
    int updatePlaceAverageRating(@Param("placeId") Long placeId);
}
