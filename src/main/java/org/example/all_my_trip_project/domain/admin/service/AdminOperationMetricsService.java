package org.example.all_my_trip_project.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dao.AdminMetricsDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminOperationCountsDTO;
import org.example.all_my_trip_project.domain.admin.dto.AdminOperationMetricsDTO;
import org.example.all_my_trip_project.domain.admin.dto.AdminPerformanceDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationMetricsService {

    /**
     * 재고 경고 기준 수량.
     *
     * <p>0으로 두면 이미 품절된 뒤에야 경고가 켜져서 손 쓸 시간이 없다. 남은 자리가 한 자릿수로
     * 떨어지면 알아채도록 5로 잡았다. 화면에도 이 기준을 함께 내려보내 숫자만 보고 판단하지
     * 않게 한다. 상품마다 규모가 달라 정확한 값은 아니고, 운영하며 조정할 자리다.
     */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final AdminMetricsDAO adminMetricsDAO;
    private final AdminPerformanceService adminPerformanceService;

    public AdminOperationMetricsDTO collect() {
        AdminOperationCountsDTO counts = adminMetricsDAO.countOperationMetrics(LOW_STOCK_THRESHOLD);
        AdminPerformanceDTO performance = adminPerformanceService.collect();

        /* 표본이 없으면 0.0%가 아니라 빈 값이다. 0%는 "오류 없음"으로 읽히는데 잰 적이 없다는 뜻이다. */
        Double errorRate = performance.sampleCount() > 0 ? performance.errorRate() : null;

        return new AdminOperationMetricsDTO(
                counts.getTodayReservations(),
                counts.getOpenInquiries(),
                counts.getLowStockSlots(),
                LOW_STOCK_THRESHOLD,
                errorRate,
                OffsetDateTime.now());
    }
}
