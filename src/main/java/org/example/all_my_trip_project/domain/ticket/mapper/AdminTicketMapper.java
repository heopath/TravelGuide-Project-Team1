package org.example.all_my_trip_project.domain.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;

import java.time.LocalDate;
import java.time.LocalTime;
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

    List<AdminTicketOptionDTO> findOptions(@Param("ticketProductId") Long ticketProductId);

    Optional<AdminTicketOptionDTO> findOptionById(@Param("optionId") Long optionId);

    Long insertOption(@Param("ticketProductId") Long ticketProductId,
                      @Param("request") AdminTicketOptionRequest request);

    int updateOption(@Param("optionId") Long optionId,
                     @Param("request") AdminTicketOptionRequest request);

    /**
     * 이름·순서 중복을 미리 본다. {@code optionId}가 있으면 자기 자신은 제외한다(수정).
     *
     * <p>DB 유니크 제약이 어차피 막지만, 제약 위반으로 터지면 이름이 겹친 것인지 순서가
     * 겹친 것인지 화면에서 구분할 수 없다.
     */
    boolean existsConflictingOption(@Param("ticketProductId") Long ticketProductId,
                                    @Param("name") String name,
                                    @Param("sortOrder") int sortOrder,
                                    @Param("optionId") Long optionId);

    Long insertSlot(@Param("optionId") Long optionId,
                    @Param("usageDate") LocalDate usageDate,
                    @Param("startTime") LocalTime startTime,
                    @Param("endTime") LocalTime endTime);

    /**
     * 시간대의 재고 행을 만든다. 시간대와 <b>같은 트랜잭션에서</b> 불려야 한다.
     *
     * <p>{@code ticket_inventory}는 시간대 ID가 PK인 1:1이고, 조회 질의들이 INNER JOIN으로
     * 묶는다. 재고 행이 없으면 시간대는 만들어졌는데 예약 화면에도, 관리자 시간대 목록에도
     * 나오지 않는다. 오류가 아니라 "없음"으로 보여 원인을 찾기 어렵다.
     */
    int insertInventory(@Param("slotId") Long slotId,
                        @Param("totalQuantity") int totalQuantity);

    /** 이미 있는 (옵션·이용일·시작시각)인지. 반복 등록에서 겹치는 날을 건너뛰는 데 쓴다. */
    boolean existsSlot(@Param("optionId") Long optionId,
                       @Param("usageDate") LocalDate usageDate,
                       @Param("startTime") LocalTime startTime);

    int updateSlotStatus(@Param("slotId") Long slotId,
                         @Param("status") String status);

    /** 이 시간대에 걸린 예약 수량. 닫아도 되는지 판단하는 데 쓴다. */
    int countSlotReserved(@Param("slotId") Long slotId);

    /** 시간대가 어느 상품에 속하는지. 경로의 상품과 맞는지 확인하는 데 쓴다. */
    Optional<Long> findProductIdBySlot(@Param("slotId") Long slotId);

    Optional<Long> findProductIdByOption(@Param("optionId") Long optionId);
}
