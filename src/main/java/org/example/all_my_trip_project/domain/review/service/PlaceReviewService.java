package org.example.all_my_trip_project.domain.review.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.review.dao.PlaceReviewDAO;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewDTO;
import org.example.all_my_trip_project.domain.review.dto.MyPlaceReviewPage;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewPage;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRatingCount;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRequest;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewSummary;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceReviewService {
    private static final int MAX_PAGE_SIZE = 50;

    private final PlaceReviewDAO reviewDAO;
    private final PlaceDAO placeDAO;
    private final ActiveMemberGuard activeMemberGuard;
    private final CacheManager cacheManager;

    public PlaceReviewPage getPage(Long placeId, Long requesterUserId, int page, int size) {
        validatePlaceId(placeId);
        validatePage(page, size);
        requirePlace(placeId);

        int offset = Math.multiplyExact(page, size);
        List<PlaceReviewDTO> fetched = reviewDAO.findPage(
                placeId, requesterUserId, offset, size + 1);
        boolean hasNext = fetched.size() > size;
        List<PlaceReviewDTO> reviews = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : fetched;
        PlaceReviewDTO myReview = requesterUserId == null
                ? null
                : reviewDAO.findByUserAndPlace(requesterUserId, placeId).orElse(null);

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 5; rating >= 1; rating--) distribution.put(rating, 0L);
        for (PlaceReviewRatingCount count : reviewDAO.countByRating(placeId)) {
            distribution.put((int) count.rating(), count.count());
        }
        PlaceReviewSummary summary = new PlaceReviewSummary(
                reviewDAO.averageVisible(placeId),
                reviewDAO.countVisible(placeId),
                distribution
        );
        return new PlaceReviewPage(
                summary, reviews, myReview, requesterUserId != null, page, size, hasNext);
    }

    public MyPlaceReviewPage getMyPage(Long userId, int page, int size) {
        validateUser(userId);
        validatePage(page, size);
        activeMemberGuard.requireActiveMember(userId);
        int offset = Math.multiplyExact(page, size);
        long totalElements = reviewDAO.countByUser(userId);
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / size);
        return new MyPlaceReviewPage(
                reviewDAO.findMyPage(userId, offset, size),
                page,
                size,
                totalElements,
                totalPages
        );
    }

    @Transactional
    public PlaceReviewDTO create(Long userId, Long placeId, PlaceReviewRequest request) {
        validateUser(userId);
        validatePlaceId(placeId);
        activeMemberGuard.requireActiveMember(userId);
        requirePlace(placeId);
        if (reviewDAO.findByUserAndPlace(userId, placeId).isPresent()) {
            throw new BusinessException(ErrorCode.PLACE_REVIEW_ALREADY_EXISTS);
        }

        PlaceReviewDTO review = PlaceReviewDTO.builder()
                .placeId(placeId)
                .userId(userId)
                .rating(request.rating())
                .content(normalizeContent(request.content()))
                .verifiedVisit(reviewDAO.hasCompletedVisit(userId, placeId))
                .status("VISIBLE")
                .build();
        try {
            if (reviewDAO.insert(review) != 1 || review.getPlaceReviewId() == null) {
                throw new IllegalStateException("장소 후기를 저장하지 못했습니다.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.PLACE_REVIEW_ALREADY_EXISTS);
        }
        updateAverageAndEvict(placeId);
        return reviewDAO.findById(review.getPlaceReviewId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_REVIEW_NOT_FOUND));
    }

    @Transactional
    public PlaceReviewDTO update(Long userId, Long reviewId, PlaceReviewRequest request) {
        validateUser(userId);
        validateReviewId(reviewId);
        activeMemberGuard.requireActiveMember(userId);
        PlaceReviewDTO existing = requireReview(reviewId, userId);
        requireOwner(userId, existing);
        existing.setRating(request.rating());
        existing.setContent(normalizeContent(request.content()));
        if (reviewDAO.update(existing) != 1) {
            throw new BusinessException(ErrorCode.PLACE_REVIEW_NOT_FOUND);
        }
        updateAverageAndEvict(existing.getPlaceId());
        return reviewDAO.findById(reviewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_REVIEW_NOT_FOUND));
    }

    @Transactional
    public void delete(Long userId, Long reviewId) {
        validateUser(userId);
        validateReviewId(reviewId);
        activeMemberGuard.requireActiveMember(userId);
        PlaceReviewDTO existing = requireReview(reviewId, userId);
        requireOwner(userId, existing);
        if (reviewDAO.delete(reviewId, userId) != 1) {
            throw new BusinessException(ErrorCode.PLACE_REVIEW_NOT_FOUND);
        }
        updateAverageAndEvict(existing.getPlaceId());
    }

    private PlaceReviewDTO requireReview(Long reviewId, Long requesterUserId) {
        return reviewDAO.findById(reviewId, requesterUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_REVIEW_NOT_FOUND));
    }

    private void updateAverageAndEvict(Long placeId) {
        reviewDAO.updatePlaceAverageRating(placeId);
        Cache cache = cacheManager.getCache("placeDetail");
        if (cache != null) cache.evict(placeId);
    }

    private void requireOwner(Long userId, PlaceReviewDTO review) {
        if (!userId.equals(review.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void requirePlace(Long placeId) {
        placeDAO.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다. placeId=" + placeId));
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("후기 내용을 입력해 주세요.");
        }
        String normalized = content.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("후기는 1000자 이하여야 합니다.");
        }
        return normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1 이상 50 이하여야 합니다.");
        }
        try {
            Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page와 size가 너무 큽니다.", exception);
        }
    }

    private void validateUser(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private void validatePlaceId(Long placeId) {
        if (placeId == null || placeId < 1) {
            throw new IllegalArgumentException("placeId는 1 이상이어야 합니다.");
        }
    }

    private void validateReviewId(Long reviewId) {
        if (reviewId == null || reviewId < 1) {
            throw new IllegalArgumentException("reviewId는 1 이상이어야 합니다.");
        }
    }
}
