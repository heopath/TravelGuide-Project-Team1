package org.example.all_my_trip_project.domain.admin.dto;

import java.util.List;

public record AdminReservationPage(
        List<AdminReservationDTO> items,
        int page,
        int size,
        long total,
        int totalPages,
        /** 만료 시각이 지났는데 PENDING으로 남은 건수. 현재 필터와 무관한 전체 기준이다. */
        long expiredPendingTotal
) {}
