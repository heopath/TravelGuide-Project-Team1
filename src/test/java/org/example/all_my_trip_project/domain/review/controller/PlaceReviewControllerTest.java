package org.example.all_my_trip_project.domain.review.controller;

import org.example.all_my_trip_project.domain.review.dto.PlaceReviewRequest;
import org.example.all_my_trip_project.domain.review.dto.PlaceReviewDTO;
import org.example.all_my_trip_project.domain.review.service.PlaceReviewService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceReviewControllerTest {
    @Mock private PlaceReviewService reviewService;
    @InjectMocks private PlaceReviewController controller;

    private final AuthenticatedUser principal =
            new AuthenticatedUser(7L, "reviewer@example.com", "USER");

    @Test
    void myReviewsUsesAuthenticatedUserId() {
        controller.myReviews(principal, 0, 10);

        verify(reviewService).getMyPage(7L, 0, 10);
    }

    @Test
    void createUsesAuthenticatedUserId() {
        PlaceReviewRequest request = new PlaceReviewRequest((short) 5, "좋은 장소예요.");
        PlaceReviewDTO saved = PlaceReviewDTO.builder().placeReviewId(10L).build();
        when(reviewService.create(7L, 301L, request)).thenReturn(saved);

        controller.create(principal, 301L, request);

        verify(reviewService).create(7L, 301L, request);
    }

    @Test
    void mutationWithoutAuthenticationIsRejected() {
        PlaceReviewRequest request = new PlaceReviewRequest((short) 4, "좋았어요.");

        assertThatThrownBy(() -> controller.create(null, 301L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
