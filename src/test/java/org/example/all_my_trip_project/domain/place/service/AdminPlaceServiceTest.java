package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.AdminPlaceRequest;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPlaceServiceTest {

    private PlaceDAO placeDAO;
    private AdminPlaceService service;

    @BeforeEach
    void setUp() {
        placeDAO = mock(PlaceDAO.class);
        /* 감사 기록은 이 테스트의 관심사가 아니다. 기록 실패가 동작을 막지 않는지는 별도 테스트에서 본다. */
        service = new AdminPlaceService(placeDAO, mock(AdminAuditService.class));
    }

    @Test
    @DisplayName("관리자가 장소와 대표 이미지를 함께 등록한다")
    void createsPlaceWithPrimaryImage() {
        doAnswer(invocation -> {
            PlaceDTO place = invocation.getArgument(0);
            place.setPlaceId(10L);
            return 1;
        }).when(placeDAO).insert(any(PlaceDTO.class));
        when(placeDAO.findById(10L)).thenReturn(Optional.of(PlaceDTO.builder()
                .placeId(10L).name("성산일출봉").category("ATTRACTION")
                .primaryImageUrl("https://images.example/place.jpg").active(true).build()));
        when(placeDAO.updatePrimaryImage(10L, "https://images.example/place.jpg", "성산일출봉 대표 이미지"))
                .thenReturn(0);

        PlaceDTO result = service.create(request("https://images.example/place.jpg"));

        assertThat(result.getPlaceId()).isEqualTo(10L);
        verify(placeDAO).insertPrimaryImage(10L, "https://images.example/place.jpg", "성산일출봉 대표 이미지");
    }

    @Test
    @DisplayName("대표 이미지 URL을 비우면 기존 대표 이미지를 제거한다")
    void removesPrimaryImageWhenBlank() {
        PlaceDTO existing = PlaceDTO.builder().placeId(10L).name("기존 장소")
                .category("CAFE").countryCode("KR").active(true).build();
        when(placeDAO.findById(10L)).thenReturn(Optional.of(existing));
        when(placeDAO.update(existing)).thenReturn(1);

        service.update(10L, request(null));

        verify(placeDAO).deletePrimaryImage(10L);
    }

    @Test
    @DisplayName("http 또는 https가 아닌 이미지 주소는 거절한다")
    void rejectsUnsafeImageUrl() {
        assertThatThrownBy(() -> service.create(request("javascript:alert(1)")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PLACE_REQUEST));
    }

    @Test
    @DisplayName("호스트가 없는 불완전한 URL은 거절한다")
    void rejectsUrlWithoutHost() {
        assertThatThrownBy(() -> service.create(request("https:place.jpg")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PLACE_REQUEST));
    }

    @Test
    @DisplayName("삭제하지 않고 공개 여부만 바꾼다")
    void hidesPlace() {
        when(placeDAO.findById(10L)).thenReturn(Optional.of(PlaceDTO.builder()
                .placeId(10L).active(true).build()));
        when(placeDAO.updateActive(10L, false)).thenReturn(1);

        PlaceDTO result = service.setVisibility(10L, false);

        verify(placeDAO).updateActive(10L, false);
        assertThat(result.getPlaceId()).isEqualTo(10L);
    }

    private AdminPlaceRequest request(String imageUrl) {
        return new AdminPlaceRequest(
                "ATTRACTION", " 성산일출봉 ", "kr", "제주특별자치도", "서귀포시",
                "성산읍", new BigDecimal("33.4587"), new BigDecimal("126.9425"),
                "제주 대표 관광지", null, "https://example.com", imageUrl, true, true);
    }
}
