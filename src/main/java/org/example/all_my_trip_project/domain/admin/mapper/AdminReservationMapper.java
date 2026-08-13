package org.example.all_my_trip_project.domain.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.admin.dto.AdminReservationDTO;

import java.util.List;

@Mapper
public interface AdminReservationMapper {

    List<AdminReservationDTO> findAdminPage(@Param("status") String status,
                                            @Param("keyword") String keyword,
                                            @Param("expiredPendingOnly") boolean expiredPendingOnly,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    long countAdmin(@Param("status") String status,
                    @Param("keyword") String keyword,
                    @Param("expiredPendingOnly") boolean expiredPendingOnly);

    long countExpiredPending();
}
