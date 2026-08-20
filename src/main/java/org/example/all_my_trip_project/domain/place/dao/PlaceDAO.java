package org.example.all_my_trip_project.domain.place.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageResult;
import org.example.all_my_trip_project.domain.place.dto.PlaceStyleResult;
import org.example.all_my_trip_project.domain.place.mapper.PlaceMapper;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class PlaceDAO {
    private final PlaceMapper placeMapper;

    public int insert(PlaceDTO place) { return placeMapper.insert(place); }
    public Long upsert(PlaceDTO place) { return placeMapper.upsert(place); }
    public Optional<PlaceDTO> findById(Long placeId) { return placeMapper.findById(placeId); }
    public List<PlaceImageResult> findImagesByPlaceId(Long placeId) {
        return placeMapper.findImagesByPlaceId(placeId);
    }
    public List<PlaceStyleResult> findStylesByPlaceId(Long placeId) {
        return placeMapper.findStylesByPlaceId(placeId);
    }
    public List<PlaceDTO> findAll() { return placeMapper.findAll(); }
    public List<PlaceDTO> findAdminPage(String keyword, String category, Boolean recommended, int offset, int size) {
        return placeMapper.findAdminPage(keyword, category, recommended, offset, size);
    }
    public long countAdmin(String keyword, String category, Boolean recommended) {
        return placeMapper.countAdmin(keyword, category, recommended);
    }
    public List<PlaceDTO> findPage(Long userId, boolean recommendedOnly, int offset, int size) {
        return placeMapper.findPage(userId, recommendedOnly, offset, size);
    }
    public List<PlaceDTO> search(Long userId, boolean recommendedOnly, String keyword, String category,
                                 String region, Long styleId, int offset, int size) {
        return placeMapper.search(userId, recommendedOnly, keyword, category, region, styleId, offset, size);
    }
    public int update(PlaceDTO place) { return placeMapper.update(place); }
    public int updateActive(Long placeId, boolean active) { return placeMapper.updateActive(placeId, active); }
    public int updateRecommended(Long placeId, boolean recommended) { return placeMapper.updateRecommended(placeId, recommended); }
    public int updateRecommendedAll(java.util.List<Long> placeIds, boolean recommended) {
        return placeMapper.updateRecommendedAll(placeIds, recommended);
    }
    public int updatePrimaryImage(Long placeId, String imageUrl, String altText) {
        return placeMapper.updatePrimaryImage(placeId, imageUrl, altText);
    }
    public int insertPrimaryImage(Long placeId, String imageUrl, String altText) {
        return placeMapper.insertPrimaryImage(placeId, imageUrl, altText);
    }
    public int deletePrimaryImage(Long placeId) { return placeMapper.deletePrimaryImage(placeId); }
    public int delete(Long placeId) { return placeMapper.delete(placeId); }
}
