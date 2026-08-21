package org.example.all_my_trip_project.domain.place.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.place.dto.RecentPlaceResult;

import java.util.List;

@Mapper
public interface PlaceViewHistoryMapper {
    int record(@Param("userId") Long userId, @Param("placeId") Long placeId);
    List<RecentPlaceResult> findRecent(@Param("userId") Long userId, @Param("size") int size);
    int deleteBeyondLimit(@Param("userId") Long userId, @Param("limit") int limit);
    int deleteByUserId(@Param("userId") Long userId);
}
