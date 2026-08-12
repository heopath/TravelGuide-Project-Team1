package org.example.all_my_trip_project.domain.review.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.review.dao.PlaceReviewDAO;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewDTO;
import org.example.all_my_trip_project.domain.review.dto.MyPlaceReviewPage;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewPage;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRatingCount;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRequest;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceReviewServiceTest {
    @Mock private PlaceReviewDAO reviewDAO;
    @Mock private PlaceDAO placeDAO;
    @Mock private ActiveMemberGuard activeMemberGuard;
    @Mock private CacheManager cacheManager;
    @InjectMocks private PlaceReviewService service;

    @Test
    void getMyPageReturnsOnlyAuthenticatedUsersReviews() {
        PlaceReviewDTO mine = review(10L, 7L, (short) 5);
        mine.setPlaceName("테라로사");
        when(reviewDAO.countByUser(7L)).thenReturn(1L);
        when(reviewDAO.findMyPage(7L, 0, 10)).thenReturn(List.of(mine));

        MyPlaceReviewPage result = service.getMyPage(7L, 0, 10);

        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.reviews()).extracting(PlaceReviewDTO::getPlaceName)
                .containsExactly("테라로사");
        verify(activeMemberGuard).requireActiveMember(7L);
    }

    @Test
    void getPageReturnsSummaryAndHasNext() {
        when(placeDAO.findById(301L)).thenReturn(Optional.of(PlaceDTO.builder().placeId(301L).build()));
        List<PlaceReviewDTO> fetched = List.of(
                review(1L, 1L, (short) 5),
                review(2L, 2L, (short) 4),
                review(3L, 3L, (short) 3)
        );
        when(reviewDAO.findPage(301L, null, 0, 3)).thenReturn(fetched);
        when(reviewDAO.averageVisible(301L)).thenReturn(new BigDecimal("4.50"));
        when(reviewDAO.countVisible(301L)).thenReturn(2L);
        when(reviewDAO.countByRating(301L)).thenReturn(List.of(
                new PlaceReviewRatingCount((short) 5, 1L),
                new PlaceReviewRatingCount((short) 4, 1L)
        ));

        PlaceReviewPage page = service.getPage(301L, null, 0, 2);

        assertThat(page.reviews()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.summary().averageRating()).isEqualByComparingTo("4.50");
        assertThat(page.summary().ratingDistribution().get(5)).isEqualTo(1L);
        assertThat(page.authenticated()).isFalse();
    }

    @Test
    void createStoresVerifiedReviewAndUpdatesAverage() {
        when(placeDAO.findById(301L)).thenReturn(Optional.of(PlaceDTO.builder().placeId(301L).build()));
        when(reviewDAO.findByUserAndPlace(7L, 301L)).thenReturn(Optional.empty());
        when(reviewDAO.hasCompletedVisit(7L, 301L)).thenReturn(true);
        when(reviewDAO.insert(any(PlaceReviewDTO.class))).thenAnswer(invocation -> {
            PlaceReviewDTO value = invocation.getArgument(0);
            value.setPlaceReviewId(10L);
            return 1;
        });
        PlaceReviewDTO saved = review(10L, 7L, (short) 5);
        saved.setPlaceId(301L);
        saved.setVerifiedVisit(true);
        when(reviewDAO.findById(10L, 7L)).thenReturn(Optional.of(saved));

        PlaceReviewDTO result = service.create(
                7L, 301L, new PlaceReviewRequest((short) 5, "  정말 좋았어요.  "));

        assertThat(result.getPlaceReviewId()).isEqualTo(10L);
        verify(reviewDAO).updatePlaceAverageRating(301L);
        verify(activeMemberGuard).requireActiveMember(7L);
    }

    @Test
    void createRejectsDuplicateReview() {
        when(placeDAO.findById(301L)).thenReturn(Optional.of(PlaceDTO.builder().placeId(301L).build()));
        when(reviewDAO.findByUserAndPlace(7L, 301L)).thenReturn(Optional.of(review(10L, 7L, (short) 5)));

        assertThatThrownBy(() -> service.create(
                7L, 301L, new PlaceReviewRequest((short) 4, "다시 방문했어요.")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_REVIEW_ALREADY_EXISTS);
    }

    @Test
    void updateRejectsReviewOwnedByAnotherUser() {
        PlaceReviewDTO existing = review(10L, 99L, (short) 5);
        existing.setPlaceId(301L);
        when(reviewDAO.findById(10L, 7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(
                7L, 10L, new PlaceReviewRequest((short) 4, "수정 내용")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private PlaceReviewDTO review(Long reviewId, Long userId, short rating) {
        return PlaceReviewDTO.builder()
                .placeReviewId(reviewId)
                .placeId(301L)
                .userId(userId)
                .rating(rating)
                .content("후기")
                .status("VISIBLE")
                .build();
    }
}
