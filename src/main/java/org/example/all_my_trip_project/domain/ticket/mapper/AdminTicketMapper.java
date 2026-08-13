package org.example.all_my_trip_project.domain.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AdminTicketMapper {

    List<AdminTicketProductDTO> findAdminPage(@Param("keyword") String keyword,
                                              @Param("status") String status,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    long countAdmin(@Param("keyword") String keyword,
                    @Param("status") String status);

    Optional<AdminTicketProductDTO> findAdminById(@Param("ticketProductId") Long ticketProductId);

    int updateStatus(@Param("ticketProductId") Long ticketProductId,
                     @Param("status") String status);
}
