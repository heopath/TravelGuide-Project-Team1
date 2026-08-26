package org.example.all_my_trip_project.domain.flight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.all_my_trip_project.domain.flight.type.BookingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 구간별 항공 예약 기록.
 *
 * <p>상태 컬럼이 없다. {@link #status()}가 두 필드에서 파생한다.
 * 파생을 저장값으로 바꾸는 순간 둘이 어긋날 수 있고, 그러면 데이터가 거짓말을 시작한다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightBookingDTO {

    private Long flightBookingId;
    private Long tripId;
    private UUID bookingBatchId;
    private Long userId;
    private Integer leg;
    private String offerId;
    private String provider;

    private String origin;
    private String destination;
    private String carrierCode;
    private String carrierName;
    private String flightNumber;
    private OffsetDateTime departureAt;
    private OffsetDateTime arrivalAt;
    private BigDecimal quotedTotalPrice;
    private String quotedCurrency;

    /** PUBLISHED / MARKET / MOCK. 이 값이 없으면 나중에 무슨 가격을 보여준 건지 밝힐 수 없다. */
    private String quotedPriceSource;
    private OffsetDateTime quotedAt;

    private boolean userReportedBooked;
    private OffsetDateTime userReportedAt;

    private String bookingRef;
    private OffsetDateTime bookingRefAddedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public BookingStatus status() {
        if (bookingRef != null && !bookingRef.isBlank()) {
            return BookingStatus.CONFIRMED;
        }
        if (userReportedBooked) {
            return BookingStatus.USER_REPORTED;
        }
        return BookingStatus.NONE;
    }
}
