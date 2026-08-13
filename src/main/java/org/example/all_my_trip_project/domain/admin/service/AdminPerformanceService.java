package org.example.all_my_trip_project.domain.admin.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminPerformanceDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 요청 지표를 MeterRegistry에서 읽어 관리자 화면이 쓸 형태로 줄인다.
 *
 * <p>Actuator 엔드포인트를 웹에 노출하지 않는다. 이 값들은 {@code /api/v1/admin/**} 뒤에 있어
 * 이미 ADMIN만 볼 수 있고, 엔드포인트를 따로 열면 노출 범위를 두 곳에서 관리하게 된다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class AdminPerformanceService {

    private static final String HTTP_TIMER = "http.server.requests";
    private static final String UPTIME_GAUGE = "process.uptime";

    /** Micrometer가 5xx 응답에 붙이는 태그 값. 4xx는 클라이언트 잘못이라 오류율에서 뺀다. */
    private static final String SERVER_ERROR = "SERVER_ERROR";

    private final MeterRegistry meterRegistry;

    public AdminPerformanceDTO collect() {
        Collection<Timer> timers = Search.in(meterRegistry).name(HTTP_TIMER).timers();

        long sampleCount = 0;
        double totalTimeMs = 0;
        long errorCount = 0;
        for (Timer timer : timers) {
            long count = timer.count();
            sampleCount += count;
            totalTimeMs += timer.totalTime(TimeUnit.MILLISECONDS);
            if (SERVER_ERROR.equals(timer.getId().getTag("outcome"))) {
                errorCount += count;
            }
        }

        double uptimeSeconds = uptimeSeconds();
        double tps = uptimeSeconds > 0 ? sampleCount / uptimeSeconds : 0;
        double averageResponseMs = sampleCount > 0 ? totalTimeMs / sampleCount : 0;
        double errorRate = sampleCount > 0 ? (double) errorCount / sampleCount : 0;

        return new AdminPerformanceDTO(
                round(tps, 2),
                round(averageResponseMs, 1),
                round(errorRate, 4),
                sampleCount,
                errorCount,
                round(uptimeSeconds, 0),
                OffsetDateTime.now());
    }

    /**
     * 가동 시간이 없으면 TPS를 계산할 수 없다.
     *
     * <p>{@code process.uptime}은 Spring Boot가 기본으로 등록하지만, 레지스트리를 직접 만들어
     * 쓰는 테스트에는 없다. 그때는 0을 돌려주고 TPS만 0이 된다. 평균 응답시간과 오류율은
     * 가동 시간과 무관하므로 그대로 나온다.
     */
    private double uptimeSeconds() {
        Gauge uptime = Search.in(meterRegistry).name(UPTIME_GAUGE).gauge();
        if (uptime == null) return 0;
        double value = uptime.value();
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    private double round(double value, int scale) {
        if (!Double.isFinite(value)) return 0;
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}
