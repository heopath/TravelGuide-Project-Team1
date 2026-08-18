package org.example.all_my_trip_project.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업을 켠다.
 *
 * <p>{@code ui} 프로필에서는 켜지 않는다. DB가 없어 정리할 대상도 없고, 화면만 보는 검수에서
 * 주기 작업이 예외를 뿜으면 로그만 어지럽다.
 *
 * <p>지금 도는 것은 만료된 티켓 예약 회수 하나뿐이다
 * ({@link org.example.all_my_trip_project.domain.ticket.service.TicketReservationExpiryService}).
 *
 * <p><b>인스턴스를 늘릴 때 주의한다.</b> 지금은 EC2 한 대라 각 작업이 한 번만 돈다. 여러 대로
 * 늘리면 같은 작업이 대수만큼 동시에 돌게 된다. 만료 정리는 행을 잠그고 {@code SKIP LOCKED}로
 * 비켜 가므로 두 번 반납되지는 않지만, 그때는 잠금을 한곳에서 잡는 방식을 따로 정해야 한다.
 */
@Configuration
@Profile("!ui")
@EnableScheduling
public class SchedulingConfig {
}
