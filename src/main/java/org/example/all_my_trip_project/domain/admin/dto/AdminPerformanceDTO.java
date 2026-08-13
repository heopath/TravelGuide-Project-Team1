package org.example.all_my_trip_project.domain.admin.dto;

import java.time.OffsetDateTime;

/**
 * 성능 모니터링 지표.
 *
 * <p>Actuator의 {@code /actuator/metrics}를 웹에 열지 않고 프로세스 안에서 MeterRegistry를
 * 직접 읽는다. 엔드포인트를 열면 노출 범위를 따로 관리해야 하고, 지금 필요한 값은 세 개뿐이다.
 *
 * <p>모든 값은 애플리케이션이 뜬 뒤부터의 누적이다. 구간 평균이 아니라서 오래 떠 있을수록
 * 최근 변화가 묻힌다. 추세를 봐야 하면 그때 수집 주기를 도입한다.
 *
 * @param sampleCount 집계에 쓰인 요청 수. 0이면 화면은 값 대신 빈 자리를 표시한다.
 */
public record AdminPerformanceDTO(
        double tps,
        double averageResponseMs,
        double errorRate,
        long sampleCount,
        long errorCount,
        double uptimeSeconds,
        OffsetDateTime collectedAt
) {}
