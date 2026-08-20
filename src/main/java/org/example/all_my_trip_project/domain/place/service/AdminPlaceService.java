package org.example.all_my_trip_project.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.AdminPlacePage;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceRequest;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPlaceService {

    private static final int MAX_PAGE_SIZE = 100;
    private final PlaceDAO placeDAO;
    private final AdminAuditService adminAuditService;

    public AdminPlacePage list(int page, int size, String keyword, String category, Boolean recommended) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
        String normalizedKeyword = text(keyword);
        String normalizedCategory = text(category);
        if (normalizedCategory != null) normalizedCategory = normalizedCategory.toUpperCase(Locale.ROOT);
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
        long total = placeDAO.countAdmin(normalizedKeyword, normalizedCategory, recommended);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new AdminPlacePage(
                placeDAO.findAdminPage(normalizedKeyword, normalizedCategory, recommended, offset, size),
                page, size, total, totalPages);
    }

    @Transactional
    public PlaceDTO create(AdminPlaceRequest request) {
        validateUrls(request);
        PlaceDTO place = apply(PlaceDTO.builder().build(), request);
        if (placeDAO.insert(place) != 1 || place.getPlaceId() == null) {
            throw new IllegalStateException("추천 장소를 등록하지 못했습니다.");
        }
        savePrimaryImage(place.getPlaceId(), place.getName(), request.primaryImageUrl());
        adminAuditService.record("PLACE_CREATE", "PLACE", place.getPlaceId(),
                null, AdminAuditService.payload("name", place.getName(), "category", place.getCategory()));
        return requirePlace(place.getPlaceId());
    }

    @Transactional
    @CacheEvict(cacheNames = "placeDetail", key = "#placeId")
    public PlaceDTO update(Long placeId, AdminPlaceRequest request) {
        validateUrls(request);
        PlaceDTO place = apply(requirePlace(placeId), request);
        if (placeDAO.update(place) != 1) throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        savePrimaryImage(placeId, place.getName(), request.primaryImageUrl());
        adminAuditService.record("PLACE_UPDATE", "PLACE", placeId,
                null, AdminAuditService.payload("name", place.getName(), "category", place.getCategory()));
        return requirePlace(placeId);
    }

    @Transactional
    @CacheEvict(cacheNames = "placeDetail", key = "#placeId")
    public PlaceDTO setVisibility(Long placeId, boolean active) {
        PlaceDTO current = requirePlace(placeId);
        if (placeDAO.updateActive(placeId, active) != 1) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        adminAuditService.record("PLACE_VISIBILITY_CHANGE", "PLACE", placeId,
                AdminAuditService.payload("active", current.getActive()),
                AdminAuditService.payload("active", active, "name", current.getName()));
        return requirePlace(placeId);
    }

    /*
     * 추천장소 화면 노출 여부. is_active(데이터 유효성)와 분리된 값이라 별도 토글이다.
     * 사용자가 일정에 담아서 생긴 장소는 기본값 FALSE로 들어오므로, 관리자가 켜야만 노출된다.
     */
    @Transactional
    @CacheEvict(cacheNames = "placeDetail", key = "#placeId")
    public PlaceDTO setRecommended(Long placeId, boolean recommended) {
        PlaceDTO current = requirePlace(placeId);
        if (placeDAO.updateRecommended(placeId, recommended) != 1) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        adminAuditService.record("PLACE_RECOMMENDATION_CHANGE", "PLACE", placeId,
                AdminAuditService.payload("recommended", current.getRecommended()),
                AdminAuditService.payload("recommended", recommended, "name", current.getName()));
        return requirePlace(placeId);
    }

    /*
     * 목록에서 고른 여러 장소를 한 번에 처리한다. 하나씩 부르면 요청마다 목록이 새로 그려져
     * 번거롭고, 중간에 실패하면 일부만 반영된 상태가 남는다. 한 트랜잭션으로 묶는다.
     */
    @Transactional
    @CacheEvict(cacheNames = "placeDetail", allEntries = true)
    public int setRecommendedAll(List<Long> placeIds, boolean recommended) {
        if (placeIds == null || placeIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
        List<Long> targets = placeIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }

        int changed = placeDAO.updateRecommendedAll(targets, recommended);
        // admin_audit_logs.action_type이 VARCHAR(30)이라 이름을 그 안에 맞춘다.
        adminAuditService.record("PLACE_RECOMMEND_BULK", "PLACE", null,
                null,
                AdminAuditService.payload("recommended", recommended, "count", changed));
        return changed;
    }

    private PlaceDTO apply(PlaceDTO place, AdminPlaceRequest request) {
        place.setCategory(request.category().trim().toUpperCase(Locale.ROOT));
        place.setName(request.name().trim());
        place.setCountryCode(request.countryCode() == null || request.countryCode().isBlank()
                ? "KR" : request.countryCode().trim().toUpperCase(Locale.ROOT));
        place.setRegion(text(request.region()));
        place.setCity(text(request.city()));
        place.setAddress(text(request.address()));
        place.setLatitude(request.latitude());
        place.setLongitude(request.longitude());
        place.setDescription(text(request.description()));
        place.setPhone(text(request.phone()));
        place.setWebsiteUrl(text(request.websiteUrl()));
        place.setActive(request.active() == null || request.active());
        // 관리자가 등록·수정하는 장소는 추천장소 화면에 노출하는 것이 기본이다.
        // 사용자가 일정에 담아서 생기는 장소는 이 경로를 타지 않아 FALSE로 남는다.
        place.setRecommended(request.recommended() == null || request.recommended());
        return place;
    }

    private void savePrimaryImage(Long placeId, String name, String imageUrl) {
        String normalized = text(imageUrl);
        if (normalized == null) {
            placeDAO.deletePrimaryImage(placeId);
            return;
        }
        int updated = placeDAO.updatePrimaryImage(placeId, normalized, name + " 대표 이미지");
        if (updated == 0) placeDAO.insertPrimaryImage(placeId, normalized, name + " 대표 이미지");
    }

    private void validateUrls(AdminPlaceRequest request) {
        validateHttpUrl(request.websiteUrl());
        validateHttpUrl(request.primaryImageUrl());
    }

    private void validateHttpUrl(String value) {
        String normalized = text(value);
        if (normalized == null) return;
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_REQUEST);
        }
    }

    private PlaceDTO requirePlace(Long placeId) {
        if (placeId == null || placeId < 1) throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        return placeDAO.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
