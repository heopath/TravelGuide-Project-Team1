package org.example.all_my_trip_project.domain.admin.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminAuditLogDTO;
import org.example.all_my_trip_project.domain.admin.mapper.AdminAuditMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class AdminAuditDAO {

    private final AdminAuditMapper adminAuditMapper;

    public int insert(AdminAuditLogDTO log) {
        return adminAuditMapper.insert(log);
    }

    public List<AdminAuditLogDTO> findRecent(String targetType, String targetId, int limit) {
        return adminAuditMapper.findRecent(targetType, targetId, limit);
    }
}
