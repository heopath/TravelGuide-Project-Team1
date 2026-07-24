package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {
    private final PlaceDAO placeDAO;

    @Transactional
    public Long create(PlaceDTO place) {
        placeDAO.insert(place);
        return place.getPlaceId();
    }

    public PlaceDTO get(Long placeId) {
        return placeDAO.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다. placeId=" + placeId));
    }

    public List<PlaceDTO> search(String keyword, String category) {
        return placeDAO.search(keyword, category);
    }

    @Transactional
    public void update(PlaceDTO place) {
        if (placeDAO.update(place) == 0) {
            throw new IllegalArgumentException("수정할 장소를 찾을 수 없습니다. placeId=" + place.getPlaceId());
        }
    }

    @Transactional
    public void delete(Long placeId) {
        if (placeDAO.delete(placeId) == 0) {
            throw new IllegalArgumentException("삭제할 장소를 찾을 수 없습니다. placeId=" + placeId);
        }
    }
}
