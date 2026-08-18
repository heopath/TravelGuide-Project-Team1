package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.ticket.dao.TicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제되지 않은 채 시간이 지난 예약을 회수한다.
 *
 * <p>예약은 만들어질 때 <b>재고를 즉시 차감</b>한다. 결제하지 않고 떠난 사람의 자리가 그대로
 * 남으면 아무도 그 자리를 살 수 없다. 대기열까지 만들어 재고를 지켰는데 그 뒤에서 새면 앞의
 * 노력이 무의미해진다.
 *
 * <p>{@code reservations.expires_at}은 예약을 만들 때 15분으로 이미 들어가고 있었다. 읽는 쪽이
 * 없었을 뿐이다.
 */
@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class TicketReservationExpiryService {

    private final TicketDAO ticketDAO;

    /**
     * 한 회차에 처리할 수 :max 건.
     *
     * <p>밀린 건이 많을 때 한 트랜잭션이 재고 행을 통째로 잠그면 그동안 아무도 예약하지
     * 못한다. 나눠서 여러 회차에 걸쳐 처리한다.
     */
    @Value("${ticket.reservation.expiry.batch-size:100}")
    private int batchSize;

    /**
     * 주기 실행. 만료 시각이 15분이라 1분 간격이면 늦어도 1분 안에 자리가 돌아온다.
     *
     * <p>{@code fixedDelay}를 쓴다. 한 회차가 길어졌을 때 다음 회차가 겹쳐 들어오면 같은
     * 행을 두고 다투기만 한다. 앞 회차가 끝난 뒤부터 세는 편이 안전하다.
     */
    @Scheduled(fixedDelayString = "${ticket.reservation.expiry.interval-ms:60000}",
            initialDelayString = "${ticket.reservation.expiry.initial-delay-ms:30000}")
    public void sweep() {
        try {
            int released = expireOnce();
            if (released > 0) {
                log.info("만료된 티켓 예약 {}건을 회수했습니다.", released);
            }
        } catch (Exception exception) {
            /*
             * 스케줄러에서 예외가 새어 나가면 다음 회차가 아예 돌지 않는다. 한 회차가 실패해도
             * 다음에 다시 시도하면 되므로 여기서 막는다.
             */
            log.warn("만료 예약 정리에 실패했습니다. 다음 회차에 다시 시도합니다.", exception);
        }
    }

    /**
     * 한 회차. 테스트와 수동 실행을 위해 따로 둔다.
     *
     * @return 자리를 되돌린 예약 수
     */
    @Transactional
    public int expireOnce() {
        List<TicketReservationDTO> expired = ticketDAO.findExpiredPending(batchSize);
        int released = 0;
        for (TicketReservationDTO reservation : expired) {
            if (ticketDAO.expireReservation(reservation.getReservationId()) != 1) {
                /* 잠갔는데도 PENDING이 아니면 그 사이 결제나 취소가 먼저 끝난 것이다. */
                continue;
            }
            if (ticketDAO.releaseInventory(reservation.getSlotId(), reservation.getQuantity()) != 1) {
                /*
                 * 재고를 되돌리지 못하면 상태만 EXPIRED로 바뀌어 자리가 영영 잠긴다.
                 * 조용히 넘기지 않고 트랜잭션을 되돌린다.
                 */
                throw new IllegalStateException(
                        "예약 " + reservation.getReservationId() + "의 재고를 되돌리지 못했습니다.");
            }
            released += 1;
        }
        return released;
    }
}
