package org.example.all_my_trip_project.domain.accommodation.dto;

import org.example.all_my_trip_project.domain.accommodation.type.AccommodationBookingStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 여행에 담긴 숙소 목록.
 *
 * @param selectedTotal 요금이 있는 숙소만 더한 합계. 요금 미제공·실습 요금은 빠진다
 * @param isEstimate    아직 자가 신고되지 않은 숙소가 있으면 참
 * @param priceSource   출처가 섞이면 {@code MIXED}. 담긴 숙소가 없으면 null
 *
 * <p>합계에서 실습 요금(SANDBOX)과 샘플(MOCK)을 빼는 이유는 화면과 같다. 그 숫자를
 * 예상 총액에 더하면 사용자가 근거 없는 금액을 믿게 된다.
 */
public record TripAccommodationsResponse(
        List<Stay> stays,
        BigDecimal selectedTotal,
        boolean isEstimate,
        boolean done,
        String priceSource
) implements Serializable {

    public static final String MIXED = "MIXED";

    private static final Set<String> EXCLUDED_FROM_TOTAL = Set.of("MOCK", "SANDBOX", "UNAVAILABLE");

    public record Stay(
            Long accommodationBookingId,
            String checkIn,
            String checkOut,
            int nights,
            AccommodationBookingStatus status,
            String offerId,
            String provider,
            String name,
            String accommodationType,
            String areaLabel,
            String address,
            Double rating,
            BigDecimal nightlyPrice,
            BigDecimal totalPrice,
            String currency,
            String priceSource,
            boolean countedInTotal,
            String bookingRef
    ) implements Serializable {}

    public static TripAccommodationsResponse from(List<AccommodationBookingDTO> bookings) {
        List<Stay> stays = bookings.stream().map(TripAccommodationsResponse::toStay).toList();

        BigDecimal total = stays.stream()
                .filter(Stay::countedInTotal)
                .map(Stay::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<String> sources = stays.stream()
                .filter(Stay::countedInTotal)
                .map(Stay::priceSource)
                .collect(Collectors.toUnmodifiableSet());

        boolean allReported = !bookings.isEmpty() && bookings.stream()
                .allMatch(booking -> booking.status() != AccommodationBookingStatus.SELECTED);

        return new TripAccommodationsResponse(
                stays,
                total,
                !allReported,
                allReported,
                sources.isEmpty() ? null : sources.size() == 1 ? sources.iterator().next() : MIXED
        );
    }

    private static Stay toStay(AccommodationBookingDTO booking) {
        boolean counted = booking.hasPrice()
                && !EXCLUDED_FROM_TOTAL.contains(booking.getQuotedPriceSource());

        return new Stay(
                booking.getAccommodationBookingId(),
                booking.getCheckIn().toString(),
                booking.getCheckOut().toString(),
                (int) ChronoUnit.DAYS.between(booking.getCheckIn(), booking.getCheckOut()),
                booking.status(),
                booking.getOfferId(),
                booking.getProvider(),
                booking.getName(),
                booking.getAccommodationType(),
                booking.getAreaLabel(),
                booking.getAddress(),
                booking.getRating(),
                booking.getQuotedNightlyPrice(),
                booking.getQuotedTotalPrice(),
                booking.getQuotedCurrency(),
                booking.getQuotedPriceSource(),
                counted,
                booking.getBookingRef()
        );
    }
}
