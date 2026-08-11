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
    Long upsert(PlaceDTO place);
    Optional<PlaceDTO> findById(Long placeId);
    List<PlaceImageResult> findImagesByPlaceId(Long placeId);
    List<PlaceStyleResult> findStylesByPlaceId(Long placeId);
    List<PlaceDTO> findAll();
    List<PlaceDTO> findAdminPage(@Param("keyword") String keyword,
                                 @Param("category") String category,
                                 @Param("active") Boolean active,
                                 @Param("offset") int offset,
                                 @Param("size") int size);
    long countAdmin(@Param("keyword") String keyword,
                    @Param("category") String category,
                    @Param("active") Boolean active);
    List<PlaceDTO> findPage(@Param("userId") Long userId,
                            @Param("offset") int offset,
                            @Param("size") int size);
    List<PlaceDTO> search(@Param("userId") Long userId,
                          @Param("keyword") String keyword,
                          @Param("category") String category,
                          @Param("region") String region,
                          @Param("styleId") Long styleId,
                          @Param("offset") int offset,
                          @Param("size") int size);
    int update(PlaceDTO place);
    int updateActive(@Param("placeId") Long placeId, @Param("active") boolean active);
    int updatePrimaryImage(@Param("placeId") Long placeId,
                           @Param("imageUrl") String imageUrl,
                           @Param("altText") String altText);
    int insertPrimaryImage(@Param("placeId") Long placeId,
                           @Param("imageUrl") String imageUrl,
                           @Param("altText") String altText);
    int deletePrimaryImage(@Param("placeId") Long placeId);
    int delete(Long placeId);
}
