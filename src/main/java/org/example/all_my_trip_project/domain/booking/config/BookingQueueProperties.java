package org.example.all_my_trip_project.domain.booking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "booking.queue")
public class BookingQueueProperties {

    /** 한 티켓 시간대에서 1초 동안 바로 예약 단계로 보낼 수 있는 요청 수. */
    private int capacityPerSecond = 5;
    /** 대기 요청과 원래 예약 입력값을 보관하는 시간. */
    private Duration entryTtl = Duration.ofMinutes(10);
    /** 입장 허용 뒤 예약 완료 API를 호출할 수 있는 시간. */
    private Duration admissionTtl = Duration.ofMinutes(2);
    /** 실제 예약 트랜잭션을 실행하는 동안 토큰을 잠그는 시간. */
    private Duration processingTtl = Duration.ofSeconds(30);
    /** 완료 응답 유실 시 같은 토큰으로 결과를 다시 받을 수 있는 시간. */
    private Duration completedTtl = Duration.ofMinutes(2);
}
