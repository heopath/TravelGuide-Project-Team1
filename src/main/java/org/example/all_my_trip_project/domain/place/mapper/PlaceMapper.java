package org.example.all_my_trip_project.domain.place.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageResult;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceStyleResult;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlaceMapper {
    int insert(PlaceDTO place);
    Optional<PlaceDTO> findById(Long placeId);
    List<PlaceImageResult> findImagesByPlaceId(Long placeId);
    List<PlaceStyleResult> findStylesByPlaceId(Long placeId);
    List<PlaceDTO> findAll();
    List<PlaceDTO> findPage(@Param("offset") int offset, @Param("size") int size);
    List<PlaceDTO> search(@Param("keyword") String keyword,
                          @Param("category") String category,
                          @Param("region") String region,
                          @Param("styleId") Long styleId,
                          @Param("offset") int offset,
                          @Param("size") int size);
    int update(PlaceDTO place);
    int delete(Long placeId);
}
