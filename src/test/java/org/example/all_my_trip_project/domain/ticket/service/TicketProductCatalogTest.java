package org.example.all_my_trip_project.domain.ticket.service;

import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketOfferDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductDetailDTO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductPage;
import org.example.all_my_trip_project.domain.ticket.dto.TicketProductSummaryDTO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상품 중심 조회. 날짜 없이 판매 중인 상품을 훑는 길이다. (#255)
 *
 * <p>기존 {@code search}는 날짜 범위가 필수고 30일로 제한돼, 9월에만 열린 티켓이 8월
 * 여행에서는 통째로 안 보였다. 이 길에는 날짜 조건이 없다.
 */
class TicketProductCatalogTest {

    private static final long PRODUCT_ID = 20L;

    private TicketDAO ticketDAO;
    private TicketService service;

    @BeforeEach
    void setUp() {
        ticketDAO = mock(TicketDAO.class);
        service = new TicketService(ticketDAO, mock(TripDAO.class));
    }

    private TicketProductSummaryDTO summary() {
        return TicketProductSummaryDTO.builder()
                .productId(PRODUCT_ID)
                .productName("모의 관광 티켓 20")
                .firstUsageDate(LocalDate.of(2026, 9, 15))
                .lastUsageDate(LocalDate.of(2026, 9, 15))
                .availableSlotCount(3)
                .remainingQuantity(594)
                .build();
    }

    @Test
    @DisplayName("날짜 없이 판매 중인 상품을 준다")
    void listsProductsWithoutDateRange() {
        when(ticketDAO.countSellableProducts(isNull())).thenReturn(20L);
        when(ticketDAO.findSellableProducts(isNull(), eq(0), eq(20))).thenReturn(List.of(summary()));

        TicketProductPage page = service.products(0, 20, null);

        assertThat(page.total()).isEqualTo(20);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getFirstUsageDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    @DisplayName("빈 검색어는 조건 없음으로 넘긴다")
    void treatsBlankKeywordAsNoFilter() {
        when(ticketDAO.countSellableProducts(isNull())).thenReturn(0L);
        when(ticketDAO.findSellableProducts(isNull(), anyInt(), anyInt())).thenReturn(List.of());

        service.products(0, 20, "   ");

        verify(ticketDAO).countSellableProducts(isNull());
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 떼고 넘긴다")
    void trimsKeyword() {
        when(ticketDAO.countSellableProducts(eq("제주"))).thenReturn(6L);
        when(ticketDAO.findSellableProducts(eq("제주"), anyInt(), anyInt())).thenReturn(List.of());

        service.products(0, 20, "  제주  ");

        verify(ticketDAO).findSellableProducts(eq("제주"), eq(0), eq(20));
    }

    @Test
    @DisplayName("쪽 크기가 한도를 넘으면 거부한다")
    void rejectsOversizedPage() {
        assertThatThrownBy(() -> service.products(0, 101, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.products(-1, 20, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("상품 상세는 그 상품의 시간대를 날짜 제한 없이 전부 준다")
    void returnsEverySlotOfProduct() {
        when(ticketDAO.findSellableProductById(PRODUCT_ID)).thenReturn(Optional.of(summary()));
        when(ticketDAO.findSlotsByProduct(PRODUCT_ID)).thenReturn(List.of(
                TicketOfferDTO.builder().slotId(58L).usageDate(LocalDate.of(2026, 9, 15)).build(),
                TicketOfferDTO.builder().slotId(59L).usageDate(LocalDate.of(2026, 12, 31)).build()));

        TicketProductDetailDTO detail = service.product(PRODUCT_ID);

        /* 9월과 12월이 함께 나온다. 30일 제한이 걸렸다면 불가능하다. */
        assertThat(detail.slots()).hasSize(2);
        assertThat(detail.product().getProductId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    @DisplayName("팔지 않는 상품은 찾을 수 없다고 한다")
    void rejectsUnsellableProduct() {
        when(ticketDAO.findSellableProductById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.product(999L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.product(null)).isInstanceOf(BusinessException.class);
    }
}
