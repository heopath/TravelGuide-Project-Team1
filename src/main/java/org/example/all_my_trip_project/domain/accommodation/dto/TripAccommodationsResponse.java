package org.example.all_my_trip_project.domain.accommodation.dto;

import org.example.all_my_trip_project.domain.accommodation.type.AccommodationBookingStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
        String priceSource,
        List<UnresolvedOutboundClick> unresolvedClicks
) implements Serializable {

    public static final String MIXED = "MIXED";

    /*
     * 더할 금액이 없는 경우만 합계에서 뺀다.
     *
     * 예전에는 실습(SANDBOX)과 샘플(MOCK)도 함께 뺐는데, 그러면 카드에는 291,200원이
     * 보이는데 예약 현황은 "요금 미정"이 되어 요금을 못 가져온 것인지 화면이 안 세는 것인지
     * 구분할 수 없었다. 항공은 같은 상황에서 샘플 운임을 합계에 넣고 출처만 밝힌다.
     *
     * 이 값들이 운영에 나갈 일은 없다. Mock provider는 @Profile("!prod"), Sandbox provider는
     * prod에서 호출되지 않고, 그래도 새어 나오면 AccommodationSearchService가 막는다.
     * 출처는 priceSource로 그대로 내려가므로 화면이 "샘플"·"실습"을 붙여 밝힌다.
     */
    private static final Set<String> EXCLUDED_FROM_TOTAL = Set.of("UNAVAILABLE");

    /**
     * 나갔는데 답을 못 받은 이탈 건.
     *
     * <p>화면이 "이 숙소 예약하셨나요?" 배너로 다시 묻는다. 숙소명을 함께 내리는 이유는
     * 검색 결과가 바뀌어 카드가 없어도 무엇을 묻는지 밝혀야 하기 때문이다.
     */
    public record UnresolvedOutboundClick(
            Long clickId,
            Long accommodationBookingId,
            String offerId,
            String name
    ) implements Serializable {}

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
        return from(bookings, List.of());
    }

    public static TripAccommodationsResponse from(List<AccommodationBookingDTO> bookings,
                                                  List<AccommodationOutboundClickDTO> unresolved) {
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
                sources.isEmpty() ? null : sources.size() == 1 ? sources.iterator().next() : MIXED,
                toUnresolvedClicks(bookings, unresolved)
        );
    }

    /**
     * 답을 못 받은 이탈 건 중 아직 물어볼 것이 남은 건만 남긴다.
     *
     * <p>이미 자가 신고했거나 예약번호까지 넣은 숙소는 답이 나온 것이라 다시 묻지 않는다.
     * 확정된 예약을 두고 "예약하셨나요?"를 물으면 사용자가 배너에서 {@code 아니요}를 눌러
     * 예약번호까지 들어간 예약을 통째로 지우게 된다. 확정한 예약 페이지를 다시 열어보는 것은
     * 자연스러운 행동이라 outcome이 비어 있는 이탈 건은 얼마든지 더 생긴다.
     *
     * <p>이 판정을 SQL로 내리지 않는 이유는 상태 컬럼이 없기 때문이다.
     * {@link AccommodationBookingDTO#status()}가 두 필드에서 파생하므로 규칙을 한곳에 둔다.
     */
    private static List<UnresolvedOutboundClick> toUnresolvedClicks(
            List<AccommodationBookingDTO> bookings, List<AccommodationOutboundClickDTO> unresolved) {

        Map<Long, AccommodationBookingDTO> bookingsById = bookings.stream()
                .collect(Collectors.toMap(AccommodationBookingDTO::getAccommodationBookingId,
                        booking -> booking, (first, second) -> first));

        return unresolved.stream()
                /* 담긴 숙소가 없는 이탈 건은 무엇을 묻는지 밝힐 수 없어 함께 뺀다. */
                .filter(click -> {
                    AccommodationBookingDTO booking = bookingsById.get(click.getAccommodationBookingId());
                    return booking != null && booking.status() == AccommodationBookingStatus.SELECTED;
                })
                .map(click -> new UnresolvedOutboundClick(
                        click.getAccommodationOutboundClickId(),
                        click.getAccommodationBookingId(),
                        click.getOfferId(),
                        bookingsById.get(click.getAccommodationBookingId()).getName()))
                .toList();
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
