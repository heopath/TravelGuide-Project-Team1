package org.example.all_my_trip_project.domain.flight;

import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;
import org.example.all_my_trip_project.domain.flight.type.BookingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태는 저장하지 않고 두 필드에서 파생한다.
 * 이 규칙이 깨지면 화면 문구·API 응답·DB가 서로 다른 말을 하기 시작한다.
 */
class FlightBookingStatusTest {

    private FlightBookingDTO booking(boolean userReportedBooked, String bookingRef) {
        return FlightBookingDTO.builder()
                .userReportedBooked(userReportedBooked)
                .bookingRef(bookingRef)
                .build();
    }

    @Test
    @DisplayName("아무것도 표시하지 않았으면 NONE")
    void noneWhenNothingReported() {
        assertThat(booking(false, null).status()).isEqualTo(BookingStatus.NONE);
    }

    @Test
    @DisplayName("자가 신고만 했으면 USER_REPORTED")
    void userReportedWhenSelfReported() {
        assertThat(booking(true, null).status()).isEqualTo(BookingStatus.USER_REPORTED);
    }

    @Test
    @DisplayName("예약번호가 들어오면 CONFIRMED로 승격한다")
    void confirmedWhenBookingRefPresent() {
        assertThat(booking(true, "ABC123").status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("자가 신고 없이 예약번호만 있어도 확정으로 본다")
    void confirmedEvenWithoutSelfReport() {
        assertThat(booking(false, "ABC123").status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("공백만 있는 예약번호는 입력하지 않은 것으로 본다")
    void blankBookingRefIsNotConfirmed() {
        assertThat(booking(true, "   ").status()).isEqualTo(BookingStatus.USER_REPORTED);
    }
}
