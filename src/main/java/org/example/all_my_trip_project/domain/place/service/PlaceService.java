package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.KakaoPlaceCreateRequest;
import org.example.all_my_trip_project.domain.place.dto.PlaceCreationResult;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDetailResult;
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
    public PlaceCreationResult findOrCreateKakaoPlace(KakaoPlaceCreateRequest request) {
        PlaceDTO candidate = PlaceDTO.builder()
                .externalProvider("KAKAO")
                .externalPlaceId(request.externalPlaceId().trim())
                .category(request.category())
                .name(request.name().trim())
                .countryCode("KR")
                .region(request.region())
                .city(request.city())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .phone(request.phone())
                .websiteUrl(request.websiteUrl())
                .active(true)
                .build();
        boolean created = placeDAO.insertKakaoIfAbsent(candidate) > 0;
        PlaceDTO place = placeDAO.findByExternal("KAKAO", candidate.getExternalPlaceId())
                .orElseThrow(() -> new IllegalStateException("카카오 장소 저장 결과를 찾을 수 없습니다."));
        return new PlaceCreationResult(place, created);
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

    public List<PlaceDTO> getPage(Long userId, int page, int size) {
        int offset = calculateOffset(page, size);
        return placeDAO.findPage(userId, offset, size);
    }

    public List<PlaceDTO> search(Long userId, String keyword, String category, String region,
                                 Long styleId, int page, int size) {
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
}
