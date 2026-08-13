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

    public List<AdminAuditLogDTO> findView(String actionType, String targetType, String targetId,
                                           Long adminUserId, int offset, int size) {
        return adminAuditMapper.findView(actionType, targetType, targetId, adminUserId, offset, size);
    }

    public long countView(String actionType, String targetType, String targetId, Long adminUserId) {
        return adminAuditMapper.countView(actionType, targetType, targetId, adminUserId);
    }

    public List<String> findActionTypes() {
        return adminAuditMapper.findActionTypes();
    }
}
