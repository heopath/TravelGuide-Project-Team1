package org.example.all_my_trip_project.domain.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.payment.dto.IssuedTicketDTO;
import org.example.all_my_trip_project.domain.payment.dto.PayableReservationDTO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentMapper {

    /** 멱등키로 앞선 결제를 찾는다. 남의 키를 조회하지 못하도록 사용자까지 함께 본다. */
    Optional<PaymentDTO> findByIdempotencyKey(@Param("userId") Long userId,
                                              @Param("idempotencyKey") String idempotencyKey);

    /**
     * 결제할 예약을 잠근다. 같은 예약에 결제가 동시에 들어와도 하나만 통과해야 한다.
     *
     * <p>예약 행만 잠근다. 재고는 예약을 만들 때 이미 차감했으므로 결제 단계에서는 건드리지
     * 않는다.
     */
    Optional<PayableReservationDTO> lockPayableReservation(@Param("userId") Long userId,
                                                           @Param("reservationId") Long reservationId);

    int insertPayment(PaymentDTO payment);

    /** 넣은 뒤 다시 읽는다. status·승인 시각은 DB가 채우므로 INSERT한 객체에는 없다. */
    Optional<PaymentDTO> findPayment(@Param("paymentId") Long paymentId);

    int confirmReservation(@Param("reservationId") Long reservationId);

    /**
     * 검증 토큰 해시를 DTO가 아니라 따로 받는다.
     *
     * <p>{@link IssuedTicketDTO}는 응답으로 나가는 객체라 해시를 담지 않는다. 필드가 있으면
     * 언젠가 조회 SQL이 채우고, 그대로 응답에 실린다. 쓰기에만 필요한 값은 쓰기 자리에서만
     * 받는다.
     */
    int insertIssuedTicket(@Param("ticket") IssuedTicketDTO ticket,
                           @Param("tokenHash") String tokenHash);

    List<IssuedTicketDTO> findTicketsByReservation(@Param("reservationId") Long reservationId);

    /**
     * 결제 결과와 함께 돌려줄 예약을 읽는다.
     *
     * <p>티켓 도메인의 DTO를 그대로 쓴다. 결제가 끝난 예약을 보여주는 화면은 예약 화면과
     * 같은 모양이어야 하는데, 여기서 별도 DTO를 만들면 두 벌을 나란히 고쳐야 한다.
     */
    Optional<org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO>
            findReservation(@Param("reservationId") Long reservationId);
}
