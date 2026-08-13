package org.example.all_my_trip_project.domain.admin.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminReservationDTO;
import org.example.all_my_trip_project.domain.admin.mapper.AdminReservationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class AdminReservationDAO {

    private final AdminReservationMapper adminReservationMapper;

    public List<AdminReservationDTO> findAdminPage(String status, String keyword,
                                                   boolean expiredPendingOnly, int offset, int size) {
        return adminReservationMapper.findAdminPage(status, keyword, expiredPendingOnly, offset, size);
    }

    public long countAdmin(String status, String keyword, boolean expiredPendingOnly) {
        return adminReservationMapper.countAdmin(status, keyword, expiredPendingOnly);
    }

    public long countExpiredPending() {
        return adminReservationMapper.countExpiredPending();
    }
}
