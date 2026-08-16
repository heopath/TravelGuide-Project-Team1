package org.example.all_my_trip_project.domain.ticket.service;

import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketReservationExpiryServiceTest {

    private TicketDAO ticketDAO;
    private TicketReservationExpiryService service;

    @BeforeEach
    void setUp() {
        ticketDAO = mock(TicketDAO.class);
        service = new TicketReservationExpiryService(ticketDAO);
        ReflectionTestUtils.setField(service, "batchSize", 100);
    }

    private TicketReservationDTO expired(long reservationId, long slotId, int quantity) {
        return TicketReservationDTO.builder()
                .reservationId(reservationId).slotId(slotId).quantity(quantity).status("PENDING")
                .build();
    }

    @Test
    @DisplayName("만료된 예약을 EXPIRED로 바꾸고 잡아 둔 수량을 되돌린다")
    void expiresAndReleasesInventory() {
        when(ticketDAO.findExpiredPending(anyInt()))
                .thenReturn(List.of(expired(1L, 10L, 2), expired(2L, 10L, 1)));
        when(ticketDAO.expireReservation(any())).thenReturn(1);
        when(ticketDAO.releaseInventory(any(), anyInt())).thenReturn(1);

        assertThat(service.expireOnce()).isEqualTo(2);

        verify(ticketDAO).releaseInventory(10L, 2);
        verify(ticketDAO).releaseInventory(10L, 1);
    }

    /*
     * 잠갔더라도 그 사이 결제나 취소가 먼저 끝났을 수 있다. 그때 재고를 또 되돌리면
     * 팔지도 않은 자리가 늘어난다.
     */
    @Test
    @DisplayName("이미 PENDING이 아니게 된 예약은 재고를 건드리지 않는다")
    void skipsWhenNoLongerPending() {
        when(ticketDAO.findExpiredPending(anyInt())).thenReturn(List.of(expired(1L, 10L, 2)));
        when(ticketDAO.expireReservation(1L)).thenReturn(0);

        assertThat(service.expireOnce()).isZero();

        verify(ticketDAO, never()).releaseInventory(any(), anyInt());
    }

    /*
     * 상태만 EXPIRED로 바뀌고 재고가 안 돌아오면 그 자리는 영영 잠긴다. 조용히 넘기면
     * 아무도 모른 채 재고가 줄어든다.
     */
    @Test
    @DisplayName("재고를 되돌리지 못하면 트랜잭션을 되돌린다")
    void failsWhenInventoryCannotBeReleased() {
        when(ticketDAO.findExpiredPending(anyInt())).thenReturn(List.of(expired(1L, 10L, 2)));
        when(ticketDAO.expireReservation(1L)).thenReturn(1);
        when(ticketDAO.releaseInventory(10L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.expireOnce())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고를 되돌리지 못했습니다");
    }

    @Test
    @DisplayName("정리할 것이 없으면 아무것도 하지 않는다")
    void doesNothingWhenNoneExpired() {
        when(ticketDAO.findExpiredPending(anyInt())).thenReturn(List.of());

        assertThat(service.expireOnce()).isZero();

        verify(ticketDAO, never()).expireReservation(any());
        verify(ticketDAO, never()).releaseInventory(any(), anyInt());
    }

    /*
     * 스케줄러에서 예외가 새어 나가면 다음 회차가 아예 돌지 않는다. 한 회차 실패가
     * 정리 자체를 멈추면 안 된다.
     */
    @Test
    @DisplayName("한 회차가 실패해도 예외를 밖으로 내보내지 않는다")
    void swallowsFailureSoNextSweepRuns() {
        when(ticketDAO.findExpiredPending(anyInt()))
                .thenThrow(new RuntimeException("데이터베이스 연결 실패"));

        service.sweep();
    }
}
