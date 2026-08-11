package org.example.all_my_trip_project.domain.accommodation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 외부 예약 사이트로 나간 이력.
 *
 * <p>복귀 감지를 놓쳐도 이 기록이 남으므로, 다음 방문에 "이 숙소 예약하셨나요?"로 다시 물어볼 수 있다.
 *
 * <p>항공은 {@code leg}로 어느 구간의 이탈인지 가리켰지만 숙박은 건수가 가변이라
 * 저장된 예약 행을 직접 참조한다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccommodationOutboundClickDTO {

    private Long accommodationOutboundClickId;
    private Long accommodationBookingId;
    private Long userId;
    private Long tripId;
    private String offerId;
    private String provider;
    private String deeplinkUrl;
    private OffsetDateTime clickedAt;

    /** visibilitychange 감지 시각. 알림 확인·앱 전환에도 발생하므로 정확한 값이 아니다. */
    private OffsetDateTime returnedAt;

    /** REPORTED_YES / REPORTED_NO / LATER / NO_RESPONSE */
    private String outcome;
}
