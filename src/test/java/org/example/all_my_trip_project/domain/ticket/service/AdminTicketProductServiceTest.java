package org.example.all_my_trip_project.domain.ticket.service;

import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.ticket.dao.AdminTicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminTicketProductServiceTest {

    private static final OffsetDateTime SALE_START =
            OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SALE_END =
            OffsetDateTime.of(2026, 9, 30, 0, 0, 0, 0, ZoneOffset.UTC);

    private AdminTicketDAO adminTicketDAO;
    private AdminTicketProductService service;

    @BeforeEach
    void setUp() {
        adminTicketDAO = mock(AdminTicketDAO.class);
        service = new AdminTicketProductService(adminTicketDAO, mock(AdminAuditService.class));
    }

    private AdminTicketProductRequest request(OffsetDateTime saleStart, OffsetDateTime saleEnd,
                                              LocalDate usageStart, LocalDate usageEnd) {
        return new AdminTicketProductRequest(3L, "해변 열차 이용권", "설명",
                saleStart, saleEnd, usageStart, usageEnd);
    }

    private AdminTicketProductRequest validRequest() {
        return request(SALE_START, SALE_END, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    private AdminTicketSlotDTO slot(int total, int reserved) {
        return AdminTicketSlotDTO.builder()
                .ticketTimeSlotId(9L).totalQuantity(total).reservedQuantity(reserved)
                .remainingQuantity(total - reserved).build();
    }

    /* ── 등록 ── */

    @Test
    @DisplayName("등록하면 RETURNING으로 받은 id의 상품을 돌려준다")
    void createsAndReadsBackById() {
        given(adminTicketDAO.existsPlace(3L)).willReturn(true);
        given(adminTicketDAO.insertProduct(any())).willReturn(77L);
        given(adminTicketDAO.findAdminById(77L)).willReturn(Optional.of(
                AdminTicketProductDTO.builder().ticketProductId(77L).status("DRAFT").build()));

        AdminTicketProductDTO created = service.create(validRequest());

        assertThat(created.getTicketProductId()).isEqualTo(77L);
        assertThat(created.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("판매 종료가 시작보다 빠르면 등록을 거부한다")
    void rejectsInvertedSalePeriod() {
        assertThatThrownBy(() -> service.create(
                request(SALE_END, SALE_START, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TICKET_REQUEST);

        verify(adminTicketDAO, never()).insertProduct(any());
    }

    @Test
    @DisplayName("이용 종료일이 시작일보다 빠르면 등록을 거부한다")
    void rejectsInvertedUsagePeriod() {
        assertThatThrownBy(() -> service.create(
                request(SALE_START, SALE_END, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1))))
                .isInstanceOf(BusinessException.class);

        verify(adminTicketDAO, never()).insertProduct(any());
    }

    @Test
    @DisplayName("없는 장소로는 등록할 수 없다")
    void rejectsUnknownPlace() {
        given(adminTicketDAO.existsPlace(3L)).willReturn(false);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);

        verify(adminTicketDAO, never()).insertProduct(any());
    }

    /* ── 수정 ── */

    @Test
    @DisplayName("수정은 판매 상태를 건드리지 않는다")
    void updateDoesNotTouchStatus() {
        given(adminTicketDAO.findAdminById(5L)).willReturn(Optional.of(
                AdminTicketProductDTO.builder().ticketProductId(5L).status("ON_SALE").build()));
        given(adminTicketDAO.existsPlace(3L)).willReturn(true);
        given(adminTicketDAO.updateProduct(anyLong(), any())).willReturn(1);

        service.update(5L, validRequest());

        verify(adminTicketDAO, never()).updateStatus(anyLong(), any());
    }

    /* ── 재고 조정 ── */

    @Test
    @DisplayName("전체 수량을 늘린다")
    void increasesTotalQuantity() {
        given(adminTicketDAO.findSlotForUpdate(9L))
                .willReturn(Optional.of(slot(30, 10)), Optional.of(slot(50, 10)));
        given(adminTicketDAO.updateInventory(9L, 50)).willReturn(1);

        AdminTicketSlotDTO result = service.changeInventory(9L, 50);

        assertThat(result.getTotalQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("이미 예약된 수보다 적게 줄이면 이유를 밝히고 거부한다")
    void rejectsReducingBelowReserved() {
        given(adminTicketDAO.findSlotForUpdate(9L)).willReturn(Optional.of(slot(30, 12)));

        assertThatThrownBy(() -> service.changeInventory(9L, 11))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_INVENTORY_BELOW_RESERVED);

        verify(adminTicketDAO, never()).updateInventory(anyLong(), anyInt());
    }

    @Test
    @DisplayName("예약 수와 같은 값까지는 줄일 수 있다")
    void allowsReducingDownToReserved() {
        given(adminTicketDAO.findSlotForUpdate(9L))
                .willReturn(Optional.of(slot(30, 12)), Optional.of(slot(12, 12)));
        given(adminTicketDAO.updateInventory(9L, 12)).willReturn(1);

        assertThat(service.changeInventory(9L, 12).getTotalQuantity()).isEqualTo(12);
    }

    @Test
    @DisplayName("잠금과 갱신 사이에 예약이 들어와 0행이 바뀌면 조용히 넘기지 않는다")
    void failsWhenConcurrentReservationBreaksCondition() {
        given(adminTicketDAO.findSlotForUpdate(9L)).willReturn(Optional.of(slot(30, 10)));
        given(adminTicketDAO.updateInventory(9L, 15)).willReturn(0);

        assertThatThrownBy(() -> service.changeInventory(9L, 15))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_INVENTORY_BELOW_RESERVED);
    }

    @Test
    @DisplayName("음수 수량은 거부한다")
    void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> service.changeInventory(9L, -1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TICKET_REQUEST);
    }

    @Test
    @DisplayName("없는 시간대는 찾을 수 없다고 알린다")
    void rejectsUnknownSlot() {
        given(adminTicketDAO.findSlotForUpdate(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeInventory(9L, 10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_FOUND);
    }
}
