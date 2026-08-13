package org.example.all_my_trip_project.domain.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.admin.dto.AdminOperationCountsDTO;

@Mapper
public interface AdminMetricsMapper {

    AdminOperationCountsDTO countOperationMetrics(@Param("lowStockThreshold") int lowStockThreshold);
}
