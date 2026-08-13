package org.example.all_my_trip_project.domain.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.admin.dto.AdminAuditLogDTO;

import java.util.List;

@Mapper
public interface AdminAuditMapper {

    int insert(AdminAuditLogDTO log);

    List<AdminAuditLogDTO> findRecent(@Param("targetType") String targetType,
                                      @Param("targetId") String targetId,
                                      @Param("limit") int limit);

    List<AdminAuditLogDTO> findView(@Param("actionType") String actionType,
                                    @Param("targetType") String targetType,
                                    @Param("targetId") String targetId,
                                    @Param("adminUserId") Long adminUserId,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    long countView(@Param("actionType") String actionType,
                   @Param("targetType") String targetType,
                   @Param("targetId") String targetId,
                   @Param("adminUserId") Long adminUserId);

    List<String> findActionTypes();
}
