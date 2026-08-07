package org.example.all_my_trip_project.domain.flight.type;

/**
 * 항공 예약 상태.
 *
 * <p>우리는 항공권을 팔지 않고 결제는 외부 사이트에서 일어난다.
 * 따라서 "예약 완료"라는 상태는 존재할 수 없다. 우리가 아는 것은 두 가지뿐이다.
 *
 * <ul>
 *   <li>{@link #USER_REPORTED} — 사용자가 예약했다고 직접 표시한 것. 검증되지 않았다.</li>
 *   <li>{@link #CONFIRMED} — 예약번호가 입력된 것. 사실상 확정으로 본다.</li>
 * </ul>
 *
 * <p>이 값은 DB 컬럼으로 저장하지 않는다.
 * {@code user_reported_booked}와 {@code booking_ref}에서 파생한다.
 */
public enum BookingStatus {
    NONE,
    USER_REPORTED,
    CONFIRMED
}
