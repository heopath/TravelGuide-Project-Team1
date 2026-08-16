package org.example.all_my_trip_project.domain.payment.dto;

import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;

import java.util.List;

/**
 * 결제 한 번의 결과를 한 덩어리로 돌려준다.
 *
 * <p>결제·예약·티켓을 따로 조회하게 두면 화면이 세 번 부르고, 그 사이에 상태가 갈릴 수 있다.
 * 결제 성공은 셋이 함께 움직이는 순간이라 함께 내려보낸다.
 */
public record PaymentResultResponse(
        PaymentDTO payment,
        TicketReservationDTO reservation,
        List<IssuedTicketDTO> tickets,
        /** 이미 결제된 건에 같은 멱등키로 다시 들어와 기존 결과를 돌려준 경우 참이다. */
        boolean replayed
) {}
