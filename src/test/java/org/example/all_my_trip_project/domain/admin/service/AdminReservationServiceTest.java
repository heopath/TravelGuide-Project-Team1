package org.example.all_my_trip_project.domain.admin.service;

import org.example.all_my_trip_project.domain.admin.dao.AdminReservationDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminReservationPage;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminReservationServiceTest {

    private AdminReservationDAO adminReservationDAO;
    private AdminReservationService service;

    @BeforeEach
    void setUp() {
        adminReservationDAO = mock(AdminReservationDAO.class);
        given(adminReservationDAO.findAdminPage(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .willReturn(List.of());
        service = new AdminReservationService(adminReservationDAO);
    }

    @Test
    @DisplayName("허용되지 않은 상태로 조회하면 거부한다")
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> service.list(0, 20, "SETTLED", null, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TICKET_REQUEST);
    }

    @Test
    @DisplayName("소문자로 들어온 상태도 받아들인다")
    void normalizesStatusCase() {
        service.list(0, 20, "pending", null, false);

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(adminReservationDAO).countAdmin(status.capture(), any(), anyBoolean());
        assertThat(status.getValue()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("만료 방치 필터를 켜면 상태 조건을 함께 걸지 않는다")
    void dropsStatusWhenFilteringExpiredPending() {
        service.list(0, 20, "CONFIRMED", null, true);

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> expired = ArgumentCaptor.forClass(Boolean.class);
        verify(adminReservationDAO).countAdmin(status.capture(), any(), expired.capture());

        assertThat(status.getValue()).isNull();
        assertThat(expired.getValue()).isTrue();
    }

    @Test
    @DisplayName("페이지 크기가 상한을 넘으면 거부한다")
    void rejectsTooLargePageSize() {
        assertThatThrownBy(() -> service.list(0, 500, null, null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("전체 건수가 0이면 페이지 수도 0이다")
    void reportsZeroPagesWhenEmpty() {
        given(adminReservationDAO.countAdmin(any(), any(), anyBoolean())).willReturn(0L);

        AdminReservationPage result = service.list(0, 20, null, null, false);

        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("방치 건수는 현재 필터와 무관하게 전체 기준으로 함께 내려보낸다")
    void alwaysReportsExpiredPendingTotal() {
        given(adminReservationDAO.countAdmin(any(), any(), anyBoolean())).willReturn(3L);
        given(adminReservationDAO.countExpiredPending()).willReturn(7L);

        AdminReservationPage result = service.list(0, 20, "CONFIRMED", null, false);

        assertThat(result.expiredPendingTotal()).isEqualTo(7);
    }
}
