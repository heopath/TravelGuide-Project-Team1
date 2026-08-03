package org.example.all_my_trip_project.domain.place.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
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
    public Optional<PlaceDTO> findById(Long placeId) { return placeMapper.findById(placeId); }
    public List<PlaceDTO> findAll() { return placeMapper.findAll(); }
    public List<PlaceDTO> search(String keyword, String category) { return placeMapper.search(keyword, category); }
    public int update(PlaceDTO place) { return placeMapper.update(place); }
    public int delete(Long placeId) { return placeMapper.delete(placeId); }
}
