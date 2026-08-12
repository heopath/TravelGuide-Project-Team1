package org.example.all_my_trip_project.domain.accommodation;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationBookingDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOutboundClickDTO;
import org.example.all_my_trip_project.domain.accommodation.dto.TripAccommodationsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재질문 배너에 무엇을 내릴지 정하는 규칙.
 *
 * <p>배너에서 {@code 아니요}를 누르면 담아둔 숙소가 지워진다. 이미 답이 나온 숙소까지
 * 물어보면 예약번호가 들어간 예약이 한 번의 클릭으로 사라진다.
 */
class TripAccommodationsResponseTest {

    private static final long BOOKING_ID = 31L;

    private AccommodationBookingDTO booking(boolean reported, String bookingRef) {
        AccommodationBookingDTO booking = new AccommodationBookingDTO();
        booking.setAccommodationBookingId(BOOKING_ID);
        booking.setCheckIn(LocalDate.of(2026, 8, 17));
        booking.setCheckOut(LocalDate.of(2026, 8, 19));
        booking.setOfferId("tour:1");
        booking.setProvider("tourapi");
        booking.setName("가나다 리조트");
        booking.setQuotedPriceSource("UNAVAILABLE");
        booking.setUserReportedBooked(reported);
        booking.setBookingRef(bookingRef);
        return booking;
    }

    private AccommodationOutboundClickDTO click(long bookingId) {
        AccommodationOutboundClickDTO click = new AccommodationOutboundClickDTO();
        click.setAccommodationOutboundClickId(55L);
        click.setAccommodationBookingId(bookingId);
        click.setOfferId("tour:1");
        return click;
    }

    @Test
    @DisplayName("아직 답하지 않은 숙소는 숙소명과 함께 다시 묻는다")
    void asksAgainWhileStillSelected() {
        TripAccommodationsResponse response = TripAccommodationsResponse.from(
                List.of(booking(false, null)), List.of(click(BOOKING_ID)));

        assertThat(response.unresolvedClicks()).singleElement()
                .satisfies(unresolved -> {
                    assertThat(unresolved.clickId()).isEqualTo(55L);
                    assertThat(unresolved.name()).isEqualTo("가나다 리조트");
                });
    }

    @Test
    @DisplayName("자가 신고한 숙소는 다시 묻지 않는다")
    void doesNotAskAfterSelfReport() {
        TripAccommodationsResponse response = TripAccommodationsResponse.from(
                List.of(booking(true, null)), List.of(click(BOOKING_ID)));

        assertThat(response.unresolvedClicks()).isEmpty();
    }

    /* 확정된 예약을 지우게 만드는 경로라 가장 중요한 케이스다. */
    @Test
    @DisplayName("예약번호까지 들어간 숙소는 다시 묻지 않는다")
    void doesNotAskAfterBookingRef() {
        TripAccommodationsResponse response = TripAccommodationsResponse.from(
                List.of(booking(true, "ABC123")), List.of(click(BOOKING_ID)));

        assertThat(response.unresolvedClicks()).isEmpty();
    }

    /* 무엇을 묻는지 밝힐 수 없는 질문은 물어볼 수 없다. */
    @Test
    @DisplayName("담긴 숙소가 없는 이탈 건은 내리지 않는다")
    void dropsClickWithoutBooking() {
        TripAccommodationsResponse response = TripAccommodationsResponse.from(
                List.of(booking(false, null)), List.of(click(999L)));

        assertThat(response.unresolvedClicks()).isEmpty();
    }

    private AccommodationBookingDTO priced(String priceSource, BigDecimal total) {
        AccommodationBookingDTO booking = booking(false, null);
        booking.setQuotedPriceSource(priceSource);
        booking.setQuotedTotalPrice(total);
        return booking;
    }

    /* 카드에 요금이 보이는데 합계만 0이면, 못 가져온 것인지 안 세는 것인지 알 수 없다. */
    @Test
    @DisplayName("샘플·실습 요금도 합계에 넣고 출처만 밝힌다")
    void countsPracticePricesInTotal() {
        assertThat(TripAccommodationsResponse.from(List.of(priced("MOCK", new BigDecimal("291200"))))
                .selectedTotal()).isEqualByComparingTo("291200");

        assertThat(TripAccommodationsResponse.from(List.of(priced("SANDBOX", new BigDecimal("180000"))))
                .selectedTotal()).isEqualByComparingTo("180000");
    }

    @Test
    @DisplayName("요금이 없는 숙소는 합계에 넣지 않는다")
    void skipsUnavailablePrice() {
        TripAccommodationsResponse response =
                TripAccommodationsResponse.from(List.of(priced("UNAVAILABLE", null)));

        assertThat(response.selectedTotal()).isEqualByComparingTo("0");
        assertThat(response.stays()).singleElement()
                .satisfies(stay -> assertThat(stay.countedInTotal()).isFalse());
    }
}
