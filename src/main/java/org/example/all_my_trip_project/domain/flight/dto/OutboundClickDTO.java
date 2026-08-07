package org.example.all_my_trip_project.domain.flight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 외부 예약 사이트로 나간 이력.
 *
 * <p>복귀 감지를 놓쳐도 이 기록이 남으므로, 다음 방문에 "이 항공편 예약하셨나요?"로 다시 물어볼 수 있다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundClickDTO {

    private Long flightOutboundClickId;
    private Long userId;
    private Long tripId;
    private Integer leg;
    private String offerId;
    private String provider;
    private String deeplinkUrl;
    private OffsetDateTime clickedAt;

    /** visibilitychange 감지 시각. 알림 확인·앱 전환에도 발생하므로 정확한 값이 아니다. */
    private OffsetDateTime returnedAt;

    /** REPORTED_YES / REPORTED_NO / LATER / NO_RESPONSE */
    private String outcome;
}
