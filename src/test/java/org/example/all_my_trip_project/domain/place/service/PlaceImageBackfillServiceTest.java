package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageBackfillResult;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceImageBackfillServiceTest {

    private PlaceDAO placeDAO;
    private TourApiPlaceImageProvider imageProvider;
    private PlaceImageBackfillService service;

    @BeforeEach
    void setUp() {
        placeDAO = mock(PlaceDAO.class);
        imageProvider = mock(TourApiPlaceImageProvider.class);
        service = new PlaceImageBackfillService(placeDAO, imageProvider, mock(AdminAuditService.class));
        when(placeDAO.insertPrimaryImage(anyLong(), anyString(), anyString())).thenReturn(1);
    }

    @Test
    @DisplayName("찾은 이미지만 대표 이미지로 넣는다")
    void fillsOnlyPlacesWithFoundImage() {
        when(placeDAO.findMissingImageCandidates(0L, 25))
                .thenReturn(List.of(place(1L, "경복궁"), place(2L, "이름없는가게")));
        when(imageProvider.findImageUrl(eq("경복궁"), any(), any()))
                .thenReturn(Optional.of("https://tour.example/gyeongbok.jpg"));
        when(imageProvider.findImageUrl(eq("이름없는가게"), any(), any())).thenReturn(Optional.empty());

        PlaceImageBackfillResult result = service.backfill(0L, null);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.filled()).isEqualTo(1);
        verify(placeDAO).insertPrimaryImage(1L, "https://tour.example/gyeongbok.jpg", "경복궁 대표 이미지");
        verify(placeDAO, never()).insertPrimaryImage(eq(2L), anyString(), anyString());
    }

    /*
     * 이 커서가 없으면 사진이 없는 장소 앞에서 계속 맴돈다. 그 장소들은 다음 조회에서도
     * 여전히 "이미지 없음"이라 같은 묶음이 다시 나오고, 그 뒤 장소에는 영영 닿지 못한다.
     */
    @Test
    @DisplayName("이미지를 하나도 못 찾아도 커서는 마지막 장소까지 넘어간다")
    void advancesCursorEvenWhenNothingFilled() {
        when(placeDAO.findMissingImageCandidates(0L, 2))
                .thenReturn(List.of(place(7L, "가"), place(9L, "나")));
        when(imageProvider.findImageUrl(anyString(), any(), any())).thenReturn(Optional.empty());

        PlaceImageBackfillResult result = service.backfill(0L, 2);

        assertThat(result.filled()).isZero();
        assertThat(result.nextAfter()).isEqualTo(9L);
        assertThat(result.done()).isFalse();
    }

    @Test
    @DisplayName("묶음이 요청한 크기보다 작으면 끝난 것으로 본다")
    void marksDoneWhenBatchIsNotFull() {
        when(placeDAO.findMissingImageCandidates(5L, 10)).thenReturn(List.of(place(6L, "가")));
        when(imageProvider.findImageUrl(anyString(), any(), any())).thenReturn(Optional.empty());

        assertThat(service.backfill(5L, 10).done()).isTrue();
    }

    @Test
    @DisplayName("더 볼 장소가 없으면 커서를 그대로 두고 끝난다")
    void stopsWhenNoCandidatesRemain() {
        when(placeDAO.findMissingImageCandidates(30L, 25)).thenReturn(List.of());

        PlaceImageBackfillResult result = service.backfill(30L, null);

        assertThat(result.scanned()).isZero();
        assertThat(result.nextAfter()).isEqualTo(30L);
        assertThat(result.done()).isTrue();
    }

    /* 한 장소가 실패했다고 나머지를 포기하면, 다시 눌러도 같은 자리에서 또 멈춘다. */
    @Test
    @DisplayName("한 장소가 실패해도 나머지는 계속 채운다")
    void keepsGoingAfterOneFailure() {
        when(placeDAO.findMissingImageCandidates(0L, 25))
                .thenReturn(List.of(place(1L, "터지는곳"), place(2L, "멀쩡한곳")));
        when(imageProvider.findImageUrl(eq("터지는곳"), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        when(imageProvider.findImageUrl(eq("멀쩡한곳"), any(), any()))
                .thenReturn(Optional.of("https://tour.example/ok.jpg"));

        PlaceImageBackfillResult result = service.backfill(0L, null);

        assertThat(result.filled()).isEqualTo(1);
        verify(placeDAO).insertPrimaryImage(2L, "https://tour.example/ok.jpg", "멀쩡한곳 대표 이미지");
    }

    @Test
    @DisplayName("커서와 묶음 크기가 범위를 벗어나면 조회하지 않는다")
    void rejectsOutOfRangeArguments() {
        assertThatThrownBy(() -> service.backfill(-1L, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PLACE_REQUEST));
        assertThatThrownBy(() -> service.backfill(0L, 0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.backfill(0L, 101))
                .isInstanceOf(BusinessException.class);

        verify(placeDAO, never()).findMissingImageCandidates(anyLong(), anyInt());
    }

    private PlaceDTO place(long placeId, String name) {
        return PlaceDTO.builder()
                .placeId(placeId).name(name)
                .latitude(new BigDecimal("37.5796")).longitude(new BigDecimal("126.9770"))
                .build();
    }
}
