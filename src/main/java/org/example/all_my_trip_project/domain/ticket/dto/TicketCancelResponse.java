package org.example.all_my_trip_project.domain.ticket.dto;

/**
 * 예약 취소 결과.
 *
 * <p>{@code refunded}로 나누는 것은 손님에게 할 말이 다르기 때문이다. 결제 전이면 "자리를
 * 놓아 드렸어요"이고, 결제 후면 "환불했고 티켓은 더 이상 쓸 수 없어요"다. 화면이 예약의
 * 이전 상태를 기억하지 않아도 되도록 서버가 알려준다.
 */
public record TicketCancelResponse(
        TicketReservationDTO reservation,
        boolean refunded,
        /** 무효 처리된 티켓 수. 결제 전 취소면 0이다. */
        int cancelledTickets
) {}
