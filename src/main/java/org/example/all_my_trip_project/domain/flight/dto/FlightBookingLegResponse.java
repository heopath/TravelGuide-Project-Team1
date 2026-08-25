package org.example.all_my_trip_project.domain.flight.dto;

import org.example.all_my_trip_project.domain.flight.type.BookingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 구간 1개의 저장 상태.
 *
 * <p>{@code status}는 서버가 파생해서 내려준다. 프론트에서 다시 계산하지 않는다.
 *
 * @param totalPrice  이탈 시점에 박제한 운임 스냅샷이다. 결제 금액이 아니다. 미선택이면 null.
 * @param priceSource 그 스냅샷이 공시운임이었는지 실판매가였는지. 미선택이면 null.
 */
public record FlightBookingLegResponse(
        int leg,
        BookingStatus status,
        String offerId,
        String carrierCode,
        String carrierName,
        String flightNumber,
        String departureTime,
        String arrivalTime,
        BigDecimal totalPrice,
        String currency,
        String priceSource,
        String bookingRef,
        String origin,
        String destination,
        OffsetDateTime departureAt,
        OffsetDateTime arrivalAt
) {
    public static FlightBookingLegResponse empty(int leg) {
        return new FlightBookingLegResponse(
                leg, BookingStatus.NONE, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
