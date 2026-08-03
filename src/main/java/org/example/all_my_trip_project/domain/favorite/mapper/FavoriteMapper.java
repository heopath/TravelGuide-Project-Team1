package org.example.all_my_trip_project.domain.favorite.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.favorite.dto.FavoriteResult;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FavoriteMapper {
    int insert(@Param("userId") Long userId, @Param("placeId") Long placeId,
               @Param("memo") String memo);
    Optional<FavoriteResult> find(@Param("userId") Long userId,
                                  @Param("placeId") Long placeId);
    List<FavoriteResult> findByUserId(@Param("userId") Long userId,
                                      @Param("offset") int offset,
                                      @Param("size") int size);
    long countByUserId(@Param("userId") Long userId);
    int delete(@Param("userId") Long userId, @Param("placeId") Long placeId);
}
