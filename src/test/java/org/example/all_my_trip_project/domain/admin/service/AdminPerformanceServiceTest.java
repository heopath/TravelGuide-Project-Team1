package org.example.all_my_trip_project.domain.admin.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.all_my_trip_project.domain.admin.dto.AdminPerformanceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPerformanceServiceTest {

    private MeterRegistry registry;
    private AdminPerformanceService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new AdminPerformanceService(registry);
    }

    private void record(String outcome, int count, long millis) {
        Timer timer = Timer.builder("http.server.requests").tag("outcome", outcome).register(registry);
        for (int index = 0; index < count; index++) {
            timer.record(Duration.ofMillis(millis));
        }
    }

    @Test
    @DisplayName("요청이 한 건도 없으면 모든 값이 0이고 표본 수도 0이다")
    void returnsZeroWhenNoRequestRecorded() {
        AdminPerformanceDTO result = service.collect();

        assertThat(result.sampleCount()).isZero();
        assertThat(result.tps()).isZero();
        assertThat(result.averageResponseMs()).isZero();
        assertThat(result.errorRate()).isZero();
    }

    @Test
    @DisplayName("평균 응답시간은 전체 요청의 누적 시간을 표본 수로 나눈 값이다")
    void averagesResponseTimeAcrossAllTimers() {
        record("SUCCESS", 3, 100);
        record("SERVER_ERROR", 1, 500);

        AdminPerformanceDTO result = service.collect();

        assertThat(result.sampleCount()).isEqualTo(4);
        assertThat(result.averageResponseMs()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("오류율은 SERVER_ERROR 응답만 센다")
    void countsOnlyServerErrorsInErrorRate() {
        record("SUCCESS", 6, 10);
        record("SERVER_ERROR", 2, 10);

        AdminPerformanceDTO result = service.collect();

        assertThat(result.errorCount()).isEqualTo(2);
        assertThat(result.errorRate()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("클라이언트 오류(4xx)는 서버 오류율에 포함하지 않는다")
    void excludesClientErrorsFromErrorRate() {
        record("SUCCESS", 2, 10);
        record("CLIENT_ERROR", 8, 10);

        AdminPerformanceDTO result = service.collect();

        assertThat(result.sampleCount()).isEqualTo(10);
        assertThat(result.errorCount()).isZero();
        assertThat(result.errorRate()).isZero();
    }

    @Test
    @DisplayName("가동 시간 지표가 없으면 TPS는 0이지만 나머지 값은 그대로 나온다")
    void keepsOtherMetricsWhenUptimeGaugeMissing() {
        record("SUCCESS", 4, 50);

        AdminPerformanceDTO result = service.collect();

        assertThat(result.tps()).isZero();
        assertThat(result.uptimeSeconds()).isZero();
        assertThat(result.averageResponseMs()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("가동 시간이 있으면 TPS는 표본 수를 가동 초로 나눈 값이다")
    void dividesSampleCountByUptimeForTps() {
        record("SUCCESS", 120, 10);
        registry.gauge("process.uptime", 60.0);

        AdminPerformanceDTO result = service.collect();

        assertThat(result.uptimeSeconds()).isEqualTo(60.0);
        assertThat(result.tps()).isEqualTo(2.0);
    }
}
