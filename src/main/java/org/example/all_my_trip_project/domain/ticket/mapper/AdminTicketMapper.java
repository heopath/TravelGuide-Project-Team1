package org.example.all_my_trip_project.domain.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;

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

    Long insertProduct(AdminTicketProductRequest request);

    int updateProduct(@Param("ticketProductId") Long ticketProductId,
                      @Param("request") AdminTicketProductRequest request);

    boolean existsPlace(@Param("placeId") Long placeId);

    List<AdminTicketSlotDTO> findSlots(@Param("ticketProductId") Long ticketProductId);

    /** 조정 전 현재 수량을 잠그고 읽는다. 동시에 두 관리자가 고치면 나중 값만 남는 것을 막는다. */
    Optional<AdminTicketSlotDTO> findSlotForUpdate(@Param("slotId") Long slotId);

    int updateInventory(@Param("slotId") Long slotId,
                        @Param("totalQuantity") int totalQuantity);
}
