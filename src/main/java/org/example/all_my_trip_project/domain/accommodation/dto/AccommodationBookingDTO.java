package org.example.all_my_trip_project.domain.accommodation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationBookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 숙소 예약 상태 한 건.
 *
 * <p>상태 컬럼이 없다. {@link #status()}가 두 필드에서 파생한다. 항공과 같은 이유다 —
 * 결제가 외부에서 일어나 "예약 완료"를 우리가 알 수 없다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AccommodationBookingDTO {

    private Long accommodationBookingId;
    private Long tripId;
    private UUID bookingBatchId;
    private Long userId;

    private LocalDate checkIn;
    private LocalDate checkOut;

    private String offerId;
    private String provider;

    private String name;
    private String accommodationType;
    private String areaLabel;
    private String address;
    private Double rating;
    private Double latitude;
    private Double longitude;

    private BigDecimal quotedNightlyPrice;
    private BigDecimal quotedTotalPrice;
    private String quotedCurrency;
    private String quotedPriceSource;
    private OffsetDateTime quotedAt;

    private Integer rooms;
    private Integer adults;

    private boolean userReportedBooked;
    private OffsetDateTime userReportedAt;

    private String bookingRef;
    private OffsetDateTime bookingRefAddedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public AccommodationBookingStatus status() {
        if (bookingRef != null && !bookingRef.isBlank()) {
            return AccommodationBookingStatus.CONFIRMED;
        }
        if (userReportedBooked) {
            return AccommodationBookingStatus.USER_REPORTED;
        }
        return AccommodationBookingStatus.SELECTED;
    }

    /** 요금이 없는 숙소는 합계에서 빠진다. 0원으로 다루면 최저가가 된다. */
    public boolean hasPrice() {
        return quotedTotalPrice != null && quotedTotalPrice.signum() > 0;
    }
}
