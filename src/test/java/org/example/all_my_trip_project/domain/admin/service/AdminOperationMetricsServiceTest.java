package org.example.all_my_trip_project.domain.admin.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.all_my_trip_project.domain.admin.dao.AdminMetricsDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminOperationCountsDTO;
import org.example.all_my_trip_project.domain.admin.dto.AdminOperationMetricsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminOperationMetricsServiceTest {

    private AdminMetricsDAO adminMetricsDAO;
    private MeterRegistry registry;
    private AdminOperationMetricsService service;

    @BeforeEach
    void setUp() {
        adminMetricsDAO = mock(AdminMetricsDAO.class);
        registry = new SimpleMeterRegistry();
        service = new AdminOperationMetricsService(adminMetricsDAO, new AdminPerformanceService(registry));
    }

    private void givenCounts(long reservations, long inquiries, long lowStock) {
        given(adminMetricsDAO.countOperationMetrics(anyInt())).willReturn(
                AdminOperationCountsDTO.builder()
                        .todayReservations(reservations)
                        .openInquiries(inquiries)
                        .lowStockSlots(lowStock)
                        .build());
    }

    private void recordRequests(String outcome, int count) {
        Timer timer = Timer.builder("http.server.requests").tag("outcome", outcome).register(registry);
        for (int index = 0; index < count; index++) {
            timer.record(Duration.ofMillis(10));
        }
    }

    @Test
    @DisplayName("DB 집계값을 그대로 전달한다")
    void passesThroughCountedValues() {
        givenCounts(12, 3, 5);

        AdminOperationMetricsDTO result = service.collect();

        assertThat(result.todayReservations()).isEqualTo(12);
        assertThat(result.openInquiries()).isEqualTo(3);
        assertThat(result.lowStockSlots()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고 경고 기준을 함께 내려보내고 같은 값으로 조회한다")
    void exposesLowStockThresholdUsedForCounting() {
        givenCounts(0, 0, 0);

        AdminOperationMetricsDTO result = service.collect();

        assertThat(result.lowStockThreshold()).isPositive();
        verify(adminMetricsDAO).countOperationMetrics(result.lowStockThreshold());
    }

    @Test
    @DisplayName("집계된 요청이 없으면 오류율은 0이 아니라 비어 있다")
    void leavesErrorRateEmptyWhenNoRequestSampled() {
        givenCounts(0, 0, 0);

        AdminOperationMetricsDTO result = service.collect();

        assertThat(result.errorRate()).isNull();
    }

    @Test
    @DisplayName("요청이 있으면 서버 오류 비율을 내려보낸다")
    void reportsErrorRateWhenRequestsExist() {
        givenCounts(0, 0, 0);
        recordRequests("SUCCESS", 3);
        recordRequests("SERVER_ERROR", 1);

        AdminOperationMetricsDTO result = service.collect();

        assertThat(result.errorRate()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("오류가 하나도 없으면 0으로 내려보낸다 — 표본이 없는 경우와 구분된다")
    void reportsZeroErrorRateWhenSampledWithoutErrors() {
        givenCounts(0, 0, 0);
        recordRequests("SUCCESS", 5);

        AdminOperationMetricsDTO result = service.collect();

        assertThat(result.errorRate()).isEqualTo(0.0);
    }
}
