package org.example.all_my_trip_project.domain.admin.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminOperationCountsDTO;
import org.example.all_my_trip_project.domain.admin.mapper.AdminMetricsMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class AdminMetricsDAO {

    private final AdminMetricsMapper adminMetricsMapper;

    public AdminOperationCountsDTO countOperationMetrics(int lowStockThreshold) {
        return adminMetricsMapper.countOperationMetrics(lowStockThreshold);
    }
}
