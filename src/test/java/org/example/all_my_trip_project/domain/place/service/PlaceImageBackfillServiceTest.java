package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageFillResult;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("고른 장소 중 사진을 찾은 것만 채운다")
    void fillsOnlySelectedPlacesWithFoundImage() {
        given(1L, place(1L, "경복궁"));
        given(2L, place(2L, "이름없는가게"));
        when(imageProvider.findImageUrl(eq("경복궁"), any(), any()))
                .thenReturn(Optional.of("https://tour.example/gyeongbok.jpg"));
        when(imageProvider.findImageUrl(eq("이름없는가게"), any(), any())).thenReturn(Optional.empty());

        PlaceImageFillResult result = service.fill(List.of(1L, 2L));

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.filled()).isEqualTo(1);
        assertThat(result.notFound()).isEqualTo(1);
        verify(placeDAO).insertPrimaryImage(1L, "https://tour.example/gyeongbok.jpg", "경복궁 대표 이미지");
        verify(placeDAO, never()).insertPrimaryImage(eq(2L), anyString(), anyString());
    }

    /* 관리자가 직접 넣은 사진을 공공데이터 사진으로 갈아치우면 안 된다. */
    @Test
    @DisplayName("이미 이미지가 있는 장소는 건드리지 않는다")
    void skipsPlacesThatAlreadyHaveImage() {
        PlaceDTO existing = place(1L, "경복궁");
        existing.setPrimaryImageUrl("https://admin.example/직접넣은사진.jpg");
        given(1L, existing);

        PlaceImageFillResult result = service.fill(List.of(1L));

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.filled()).isZero();
        verify(imageProvider, never()).findImageUrl(anyString(), any(), any());
    }

    @Test
    @DisplayName("좌표가 없으면 찾을 방법이 없으므로 건너뛴다")
    void skipsPlacesWithoutCoordinates() {
        PlaceDTO noCoordinates = PlaceDTO.builder().placeId(1L).name("좌표없음").build();
        given(1L, noCoordinates);

        assertThat(service.fill(List.of(1L)).skipped()).isEqualTo(1);
        verify(imageProvider, never()).findImageUrl(anyString(), any(), any());
    }

    /* 한 장소가 실패했다고 나머지를 포기하면, 다시 눌러도 같은 자리에서 또 멈춘다. */
    @Test
    @DisplayName("한 장소가 실패해도 나머지는 계속 채운다")
    void keepsGoingAfterOneFailure() {
        given(1L, place(1L, "터지는곳"));
        given(2L, place(2L, "멀쩡한곳"));
        when(imageProvider.findImageUrl(eq("터지는곳"), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        when(imageProvider.findImageUrl(eq("멀쩡한곳"), any(), any()))
                .thenReturn(Optional.of("https://tour.example/ok.jpg"));

        PlaceImageFillResult result = service.fill(List.of(1L, 2L));

        assertThat(result.filled()).isEqualTo(1);
        verify(placeDAO).insertPrimaryImage(2L, "https://tour.example/ok.jpg", "멀쩡한곳 대표 이미지");
    }

    @Test
    @DisplayName("같은 장소를 두 번 골라도 한 번만 부른다")
    void callsOnceForDuplicatedSelection() {
        given(1L, place(1L, "경복궁"));
        when(imageProvider.findImageUrl(anyString(), any(), any()))
                .thenReturn(Optional.of("https://tour.example/gyeongbok.jpg"));

        assertThat(service.fill(List.of(1L, 1L)).requested()).isEqualTo(1);
        verify(imageProvider).findImageUrl(eq("경복궁"), any(), any());
    }

    @Test
    @DisplayName("선택이 비었거나 한 페이지를 넘으면 거절한다")
    void rejectsEmptyOrOversizedSelection() {
        assertThatThrownBy(() -> service.fill(List.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PLACE_REQUEST));
        assertThatThrownBy(() -> service.fill(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.fill(
                IntStream.rangeClosed(1, 101).mapToObj(Long::valueOf).toList()))
                .isInstanceOf(BusinessException.class);

        verify(placeDAO, never()).findById(anyLong());
    }

    private void given(long placeId, PlaceDTO place) {
        when(placeDAO.findById(placeId)).thenReturn(Optional.of(place));
    }

    private PlaceDTO place(long placeId, String name) {
        return PlaceDTO.builder()
                .placeId(placeId).name(name)
                .latitude(new BigDecimal("37.5796")).longitude(new BigDecimal("126.9770"))
                .build();
    }
}
