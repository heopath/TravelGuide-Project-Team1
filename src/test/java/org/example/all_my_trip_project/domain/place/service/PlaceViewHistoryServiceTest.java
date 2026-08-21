package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceViewHistoryDAO;
import org.example.all_my_trip_project.domain.place.dto.RecentPlaceResult;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceViewHistoryServiceTest {

    private PlaceViewHistoryDAO dao;
    private PlaceViewHistoryService service;

    @BeforeEach
    void setUp() {
        dao = mock(PlaceViewHistoryDAO.class);
        service = new PlaceViewHistoryService(dao);
    }

    @Test
    @DisplayName("장소를 보면 기록하고 상한을 넘은 오래된 이력을 정리한다")
    void recordsAndTrims() {
        service.record(7L, 42L);

        verify(dao).record(7L, 42L);
        verify(dao).deleteBeyondLimit(7L, 30);
    }

    /*
     * 이력은 곁다리다. 이것 때문에 장소 상세가 안 보이면 훨씬 나쁘다.
     * 없는 장소를 열어보려 해 FK에 걸리는 경우도 여기로 온다.
     */
    @Test
    @DisplayName("기록이 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsRecordFailure() {
        when(dao.record(anyLong(), anyLong())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service.record(7L, 42L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("장소 번호가 잘못되면 조회하지 않는다")
    void rejectsBadPlaceId() {
        assertThatThrownBy(() -> service.record(7L, 0L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PLACE_REQUEST));

        verify(dao, never()).record(anyLong(), anyLong());
    }

    @Test
    @DisplayName("크기를 주지 않으면 기본값으로 조회한다")
    void usesDefaultSize() {
        when(dao.findRecent(7L, PlaceViewHistoryService.DEFAULT_SIZE))
                .thenReturn(List.of(new RecentPlaceResult()));

        assertThat(service.findRecent(7L, null)).hasSize(1);
        verify(dao).findRecent(7L, PlaceViewHistoryService.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("보관 상한을 넘는 크기는 거절한다")
    void rejectsOversizedRequest() {
        assertThatThrownBy(() -> service.findRecent(7L, 31)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.findRecent(7L, 0)).isInstanceOf(BusinessException.class);

        verify(dao, never()).findRecent(anyLong(), anyInt());
    }

    @Test
    @DisplayName("로그인하지 않았으면 목록을 주지 않는다")
    void rejectsAnonymousRead() {
        assertThatThrownBy(() -> service.findRecent(null, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
