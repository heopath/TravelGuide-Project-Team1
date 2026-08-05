package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.KakaoPlaceCreateRequest;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.dto.PlaceCreationResult;
import org.example.all_my_trip_project.domain.place.dto.PlaceDetailResult;
import org.example.all_my_trip_project.domain.place.dto.PlaceImageResult;
import org.example.all_my_trip_project.domain.place.dto.PlaceStyleResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceDAO placeDAO;

    @InjectMocks
    private PlaceService placeService;

    @Test
    void createsKakaoPlaceOnceAndReturnsPersistedPlace() {
        KakaoPlaceCreateRequest request = kakaoRequest("12345");
        PlaceDTO persisted = PlaceDTO.builder()
                .placeId(100L)
                .externalProvider("KAKAO")
                .externalPlaceId("12345")
                .name("해운대")
                .build();
        when(placeDAO.insertKakaoIfAbsent(any(PlaceDTO.class))).thenReturn(1);
        when(placeDAO.findByExternal("KAKAO", "12345")).thenReturn(java.util.Optional.of(persisted));

        PlaceCreationResult result = placeService.findOrCreateKakaoPlace(request);

        assertThat(result.created()).isTrue();
        assertThat(result.place()).isSameAs(persisted);
    }

    @Test
    void duplicateKakaoPlaceReturnsExistingPlace() {
        KakaoPlaceCreateRequest request = kakaoRequest("12345");
        PlaceDTO existing = PlaceDTO.builder().placeId(100L).externalPlaceId("12345").build();
        when(placeDAO.insertKakaoIfAbsent(any(PlaceDTO.class))).thenReturn(0);
        when(placeDAO.findByExternal("KAKAO", "12345")).thenReturn(java.util.Optional.of(existing));

        PlaceCreationResult result = placeService.findOrCreateKakaoPlace(request);

        assertThat(result.created()).isFalse();
        assertThat(result.place()).isSameAs(existing);
    }

    private KakaoPlaceCreateRequest kakaoRequest(String externalPlaceId) {
        return new KakaoPlaceCreateRequest(
                externalPlaceId, "ATTRACTION", "해운대", "부산", "해운대구", "부산 해운대구",
                new BigDecimal("35.1587"), new BigDecimal("129.1604"), null,
                "https://place.map.kakao.com/12345"
        );
    }

    @Test
    void getDetailCombinesPlaceImagesAndStyles() {
        PlaceDTO place = PlaceDTO.builder().placeId(100L).name("서울 관광지").build();
        PlaceImageResult image = new PlaceImageResult(1L, "https://example.com/place.jpg",
                "서울 관광지", 1, true);
        PlaceStyleResult style = new PlaceStyleResult(1L, "SIGHTSEEING", "관광", 95);
        when(placeDAO.findById(100L)).thenReturn(java.util.Optional.of(place));
        when(placeDAO.findImagesByPlaceId(100L)).thenReturn(List.of(image));
        when(placeDAO.findStylesByPlaceId(100L)).thenReturn(List.of(style));

        PlaceDetailResult result = placeService.getDetail(100L);

        assertThat(result.getPlace()).isSameAs(place);
        assertThat(result.getImages()).containsExactly(image);
        assertThat(result.getStyles()).containsExactly(style);
    }

    @Test
    void getDetailStopsWhenPlaceDoesNotExist() {
        when(placeDAO.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> placeService.getDetail(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("장소를 찾을 수 없습니다. placeId=999");
    }

    @Test
    void getPageCalculatesOffsetAndReturnsPlaces() {
        PlaceDTO firstPlace = PlaceDTO.builder()
                .placeId(41L)
                .name("부산 해운대")
                .build();
        when(placeDAO.findPage(7L, 40, 20)).thenReturn(List.of(firstPlace));

        List<PlaceDTO> result = placeService.getPage(7L, 2, 20);

        assertThat(result).containsExactly(firstPlace);
        verify(placeDAO).findPage(7L, 40, 20);
    }

    @Test
    void getPageReturnsEmptyListWhenNoPlacesExist() {
        when(placeDAO.findPage(null, 0, 20)).thenReturn(List.of());

        List<PlaceDTO> result = placeService.getPage(null, 0, 20);

        assertThat(result).isEmpty();
        verify(placeDAO).findPage(null, 0, 20);
    }

    @Test
    void getPageRejectsNegativePage() {
        assertThatThrownBy(() -> placeService.getPage(null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page는 0 이상이어야 합니다.");
    }

    @Test
    void getPageRejectsSizeLargerThanMaximum() {
        assertThatThrownBy(() -> placeService.getPage(null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size는 1 이상 100 이하여야 합니다.");
    }

    @Test
    void searchNormalizesConditionsAndAppliesPagination() {
        PlaceDTO cafe = PlaceDTO.builder()
                .placeId(89L)
                .name("서울 CAFE 089")
                .category("CAFE")
                .region("서울")
                .build();
        when(placeDAO.search(7L, "서울", "CAFE", "서울", null, 10, 10))
                .thenReturn(List.of(cafe));

        List<PlaceDTO> result = placeService.search(7L, "  서울  ", " cafe ", " 서울 ", null, 1, 10);

        assertThat(result).containsExactly(cafe);
        verify(placeDAO).search(7L, "서울", "CAFE", "서울", null, 10, 10);
    }

    @Test
    void searchConvertsBlankConditionsToNull() {
        when(placeDAO.search(null, null, null, null, null, 0, 20)).thenReturn(List.of());

        List<PlaceDTO> result = placeService.search(null, " ", null, "\t", null, 0, 20);

        assertThat(result).isEmpty();
        verify(placeDAO).search(null, null, null, null, null, 0, 20);
    }

    @Test
    void searchAppliesTravelStyleFilter() {
        PlaceDTO styledPlace = PlaceDTO.builder()
                .placeId(88L)
                .name("인천 ACTIVITY 088")
                .build();
        when(placeDAO.search(7L, null, null, null, 4L, 0, 20))
                .thenReturn(List.of(styledPlace));

        List<PlaceDTO> result = placeService.search(7L, null, null, null, 4L, 0, 20);

        assertThat(result).containsExactly(styledPlace);
        verify(placeDAO).search(7L, null, null, null, 4L, 0, 20);
    }

    @Test
    void searchRejectsNonPositiveStyleId() {
        assertThatThrownBy(() -> placeService.search(null, null, null, null, 0L, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("styleId는 1 이상이어야 합니다.");
    }
}
