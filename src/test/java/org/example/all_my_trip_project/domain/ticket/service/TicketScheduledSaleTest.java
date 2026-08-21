package org.example.all_my_trip_project.domain.ticket.service;

import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.CreateTicketReservationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductSummaryDTO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지정 시각 판매. (#256)
 *
 * <p>오픈 전 상품을 <b>목록에는 보여주되 못 사게</b> 하는 것이 요점이다. 미리 보여야 손님이
 * 그 시각에 모이고, 그 전에 살 수 있으면 지정 시각 판매라고 할 수 없다.
 *
 * <p>상태 계산은 질의가 서버 시각으로 한다. 여기서는 서비스가 그 값을 어떻게 다루는지 본다.
 */
class TicketScheduledSaleTest {

    private static final long SLOT_ID = 55L;
    private static final long USER_ID = 7L;

    private TicketDAO ticketDAO;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketDAO = mock(TicketDAO.class);
        service = new TicketService(ticketDAO, mock(TripDAO.class));
    }

    private TicketOfferDTO offer(String saleState) {
        return TicketOfferDTO.builder()
                .slotId(SLOT_ID)
                .productName("한정 판매 입장권")
                .optionName("성인")
                .usageDate(LocalDate.now().plusDays(10))
                .unitPrice(new BigDecimal("20000"))
                .currency("KRW")
                .maxQuantityPerUser(4)
                .remainingQuantity(10)
                .saleType("SCHEDULED")
                .saleState(saleState)
                .opensAt("SCHEDULED".equals(saleState) ? OffsetDateTime.now().plusHours(3) : null)
                .build();
    }

    private CreateTicketReservationRequest request() {
        return new CreateTicketReservationRequest(null, SLOT_ID, 2, "req-1");
    }

    /* ── 예약 ── */

    /*
     * 조회에서만 막으면 API를 직접 부르는 쪽이 그대로 뚫는다. 오픈 예정 상품은 목록에
     * 보이므로 slotId를 알아내기도 쉽다.
     */
    @Test
    @DisplayName("오픈 전에는 예약이 거부된다")
    void refusesReservationBeforeOpen() {
        when(ticketDAO.findByRequestKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(ticketDAO.findSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(offer("SCHEDULED")));

        assertThatThrownBy(() -> service.reserve(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_SALE_NOT_OPEN);

        /* 재고를 건드리기 전에 막아야 한다. 잡았다 되돌리면 그사이 다른 사람이 못 산다. */
        verify(ticketDAO, never()).reserveInventory(anyLong(), anyInt());
        verify(ticketDAO, never()).insertReservation(any());
    }

    /* 오픈 전과 판매 종료는 손님에게 해 줄 말이 다르다. */
    @Test
    @DisplayName("판매가 끝났으면 끝났다고 알린다")
    void refusesReservationAfterEnd() {
        when(ticketDAO.findByRequestKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(ticketDAO.findSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(offer("ENDED")));

        assertThatThrownBy(() -> service.reserve(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_SALE_ENDED);
    }

    @Test
    @DisplayName("오픈한 뒤에는 예약된다")
    void reservesAfterOpen() {
        when(ticketDAO.findByRequestKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(ticketDAO.findSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(offer("ON_SALE")));
        when(ticketDAO.reserveInventory(SLOT_ID, 2)).thenReturn(1);

        assertThat(service.reserve(USER_ID, request()).getStatus()).isEqualTo("PENDING");
        verify(ticketDAO).insertReservation(any());
    }

    /* ── 대기열 진입 ── */

    /*
     * 줄부터 서게 두면 승급된 뒤 예약 단계에서야 거절당한다. 그때는 기다린 시간이 버려진 뒤다.
     */
    @Test
    @DisplayName("오픈 전에는 대기열에도 못 선다")
    void refusesQueueBeforeOpen() {
        when(ticketDAO.findSlot(SLOT_ID)).thenReturn(Optional.of(offer("SCHEDULED")));

        assertThatThrownBy(() -> service.requireSaleOpen(SLOT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_SALE_NOT_OPEN);
    }

    @Test
    @DisplayName("오픈한 뒤에는 대기열에 설 수 있다")
    void allowsQueueAfterOpen() {
        when(ticketDAO.findSlot(SLOT_ID)).thenReturn(Optional.of(offer("ON_SALE")));

        service.requireSaleOpen(SLOT_ID);
    }

    /* 대기열은 잠그지 않고 본다. 오픈 직후 줄 서는 사람마다 재고 행을 잠그면 서로 기다린다. */
    @Test
    @DisplayName("대기열 확인은 재고를 잠그지 않는다")
    void queueCheckDoesNotLock() {
        when(ticketDAO.findSlot(SLOT_ID)).thenReturn(Optional.of(offer("ON_SALE")));

        service.requireSaleOpen(SLOT_ID);

        verify(ticketDAO, never()).findSlotForUpdate(anyLong());
    }

    /* ── 목록 ── */

    /*
     * 오픈 예정 상품이 목록에 실려 오고, 화면이 쓸 값(상태·오픈 시각·서버 시각)이 함께 온다.
     * 남은 시간을 손님 기기 시계로 세면 시계가 틀어진 사람은 일찍 눌러 실패한다.
     */
    @Test
    @DisplayName("목록은 서버 시각을 함께 내린다")
    void listCarriesServerTime() {
        OffsetDateTime opensAt = OffsetDateTime.now().plusHours(5);
        TicketProductSummaryDTO upcoming = TicketProductSummaryDTO.builder()
                .productId(9L)
                .productName("한정 판매 입장권")
                .saleType("SCHEDULED")
                .saleState("SCHEDULED")
                .opensAt(opensAt)
                .build();
        when(ticketDAO.countSellableProducts("")).thenReturn(1L);
        when(ticketDAO.findSellableProducts("", 0, 20)).thenReturn(List.of(upcoming));

        var page = service.products(0, 20, null);

        assertThat(page.serverTime()).isNotNull();
        assertThat(page.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getSaleState()).isEqualTo("SCHEDULED");
                    assertThat(item.getOpensAt()).isEqualTo(opensAt);
                });
    }
}
