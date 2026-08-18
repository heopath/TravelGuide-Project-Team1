package org.example.all_my_trip_project.domain.ticket.service;

import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.ticket.dao.AdminTicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotCreateResponse;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotRequest;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 시간대를 만들 때 재고 행이 반드시 함께 만들어지는지 본다.
 *
 * <p>이 조합이 어긋나면 조용히 실패한다. {@code ticket_inventory}가 없는 시간대는 조회
 * 질의들의 INNER JOIN에서 빠져 예약 화면에도, 관리자 시간대 목록에도 나오지 않는다.
 * 오류가 아니라 "없음"으로 보이므로 사람이 눈으로 잡을 수 없다.
 */
class AdminTicketSlotCreationTest {

    private static final long PRODUCT_ID = 7L;
    private static final long OPTION_ID = 70L;

    private AdminTicketDAO dao;
    private AdminTicketProductService service;

    @BeforeEach
    void setUp() {
        dao = mock(AdminTicketDAO.class);
        service = new AdminTicketProductService(dao, mock(AdminAuditService.class));

        when(dao.findAdminById(PRODUCT_ID)).thenReturn(Optional.of(
                AdminTicketProductDTO.builder().ticketProductId(PRODUCT_ID).name("모의 티켓").build()));
        when(dao.findProductIdByOption(OPTION_ID)).thenReturn(Optional.of(PRODUCT_ID));
        when(dao.findSlots(PRODUCT_ID)).thenReturn(List.of());
        when(dao.existsSlot(anyLong(), any(), any())).thenReturn(false);

        AtomicLong sequence = new AtomicLong(1000L);
        when(dao.insertSlot(anyLong(), any(), any(), any()))
                .thenAnswer(invocation -> sequence.incrementAndGet());
        when(dao.insertInventory(anyLong(), anyInt())).thenReturn(1);
    }

    private AdminTicketSlotRequest request(LocalDate from, LocalDate to, Set<DayOfWeek> weekdays) {
        return new AdminTicketSlotRequest(
                OPTION_ID, from, to, weekdays, LocalTime.of(10, 0), LocalTime.of(12, 0), 100);
    }

    @Test
    @DisplayName("시간대를 만들면 같은 수만큼 재고 행도 만든다")
    void createsInventoryForEverySlot() {
        AdminTicketSlotCreateResponse result = service.createSlots(PRODUCT_ID,
                request(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), null));

        assertThat(result.created()).isEqualTo(5);
        assertThat(result.skipped()).isZero();
        verify(dao, times(5)).insertSlot(eq(OPTION_ID), any(), any(), any());
        /* 핵심. 시간대 5개에 재고도 정확히 5개여야 한다. */
        verify(dao, times(5)).insertInventory(anyLong(), eq(100));
    }

    @Test
    @DisplayName("재고 행이 만들어지지 않으면 예외로 끊어 트랜잭션을 되돌린다")
    void failsWhenInventoryIsNotCreated() {
        when(dao.insertInventory(anyLong(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> service.createSlots(PRODUCT_ID,
                request(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 있는 날짜는 건너뛰고 몇 개를 건너뛰었는지 알린다")
    void skipsExistingDatesInsteadOfFailing() {
        when(dao.existsSlot(eq(OPTION_ID), eq(LocalDate.of(2026, 9, 2)), any())).thenReturn(true);

        AdminTicketSlotCreateResponse result = service.createSlots(PRODUCT_ID,
                request(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), null));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        /* 건너뛴 날에는 재고도 만들지 않아야 한다. 만들면 주인 없는 행이 남는다. */
        verify(dao, times(2)).insertInventory(anyLong(), anyInt());
    }

    @Test
    @DisplayName("요일을 고르면 그 요일에만 만든다")
    void createsOnlyOnSelectedWeekdays() {
        /* 2026-09-01은 화요일이다. 한 주에서 화·목만 고르면 2개다. */
        AdminTicketSlotCreateResponse result = service.createSlots(PRODUCT_ID,
                request(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7),
                        Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)));

        assertThat(result.created()).isEqualTo(2);
        verify(dao, times(2)).insertInventory(anyLong(), eq(100));
    }

    @Test
    @DisplayName("다른 상품의 옵션에는 시간대를 붙일 수 없다")
    void rejectsOptionOwnedByAnotherProduct() {
        when(dao.findProductIdByOption(OPTION_ID)).thenReturn(Optional.of(PRODUCT_ID + 1));

        assertThatThrownBy(() -> service.createSlots(PRODUCT_ID,
                request(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null)))
                .isInstanceOf(BusinessException.class);
    }
}
