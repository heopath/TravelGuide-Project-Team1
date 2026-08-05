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

    public int insertKakaoIfAbsent(PlaceDTO place) { return placeMapper.insertKakaoIfAbsent(place); }
    public Optional<PlaceDTO> findByExternal(String provider, String externalPlaceId) {
        return placeMapper.findByExternal(provider, externalPlaceId);
    }
    public Optional<PlaceDTO> findById(Long placeId) { return placeMapper.findById(placeId); }
    public List<PlaceImageResult> findImagesByPlaceId(Long placeId) {
        return placeMapper.findImagesByPlaceId(placeId);
    }
    public List<PlaceStyleResult> findStylesByPlaceId(Long placeId) {
        return placeMapper.findStylesByPlaceId(placeId);
    }
    public List<PlaceDTO> findAll() { return placeMapper.findAll(); }
    public List<PlaceDTO> findPage(Long userId, int offset, int size) {
        return placeMapper.findPage(userId, offset, size);
    }
    public List<PlaceDTO> search(Long userId, String keyword, String category, String region,
                                 Long styleId, int offset, int size) {
        return placeMapper.search(userId, keyword, category, region, styleId, offset, size);
    }
}
