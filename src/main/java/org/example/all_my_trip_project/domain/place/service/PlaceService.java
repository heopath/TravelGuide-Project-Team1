package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDetailResult;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {
    private static final int MAX_PAGE_SIZE = 100;

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

    @Cacheable(cacheNames = "placeDetail", key = "#placeId")
    public PlaceDetailResult getDetail(Long placeId) {
        PlaceDTO place = get(placeId);
        return new PlaceDetailResult(
                place,
                placeDAO.findImagesByPlaceId(placeId),
                placeDAO.findStylesByPlaceId(placeId)
        );
    }

    public List<PlaceDTO> getPage(Long userId, boolean recommendedOnly, int page, int size) {
        int offset = calculateOffset(page, size);
        return placeDAO.findPage(userId, recommendedOnly, offset, size);
    }

    public List<PlaceDTO> search(Long userId, boolean recommendedOnly, String keyword, String category,
                                 String region, Long styleId, int page, int size) {
        int offset = calculateOffset(page, size);
        String normalizedKeyword = normalize(keyword);
        String normalizedCategory = normalize(category);
        String normalizedRegion = normalize(region);

        if (styleId != null && styleId < 1) {
            throw new IllegalArgumentException("styleId는 1 이상이어야 합니다.");
        }
        if (normalizedCategory != null) {
            normalizedCategory = normalizedCategory.toUpperCase(Locale.ROOT);
        }
        return placeDAO.search(
                userId,
                recommendedOnly,
                normalizedKeyword,
                normalizedCategory,
                normalizedRegion,
                styleId,
                offset,
                size
        );
    }

    private int calculateOffset(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }

        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page와 size가 너무 큽니다.", exception);
        }
        return offset;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
    
    // update()·delete()는 공개 API로 노출하지 않는다. 관리자용 장소 관리와 외부 동기화(2차 구현)에서 사용 예정.
    @Transactional
    @CacheEvict(cacheNames = "placeDetail", key = "#place.placeId")
    public void update(PlaceDTO place) {
        if (placeDAO.update(place) == 0) {
            throw new IllegalArgumentException("수정할 장소를 찾을 수 없습니다. placeId=" + place.getPlaceId());
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "placeDetail", key = "#placeId")
    public void delete(Long placeId) {
        if (placeDAO.delete(placeId) == 0) {
            throw new IllegalArgumentException("삭제할 장소를 찾을 수 없습니다. placeId=" + placeId);
        }
    }
}
