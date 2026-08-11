package org.example.all_my_trip_project.domain.accommodation.dto;

import jakarta.validation.constraints.Size;

/**
 * 예약번호 입력. 값이 들어오면 상태가 확정으로 승격한다.
 *
 * <p>빈 문자열을 허용하는 이유는 사용자가 잘못 넣은 번호를 지울 수 있어야 하기 때문이다.
 * 지우면 다시 자가 신고 상태로 내려간다.
 */
public record AccommodationBookingRefRequest(
        @Size(max = 32) String bookingRef
) {}
