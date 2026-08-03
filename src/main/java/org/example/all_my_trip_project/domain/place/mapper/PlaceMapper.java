package org.example.all_my_trip_project.domain.place.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlaceMapper {
    int insert(PlaceDTO place);
    Optional<PlaceDTO> findById(Long placeId);
    List<PlaceDTO> findAll();
    List<PlaceDTO> search(@Param("keyword") String keyword, @Param("category") String category);
    int update(PlaceDTO place);
    int delete(Long placeId);
}
