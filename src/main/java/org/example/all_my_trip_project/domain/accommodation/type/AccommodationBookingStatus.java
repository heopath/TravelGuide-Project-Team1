package org.example.all_my_trip_project.domain.accommodation.type;

/**
 * 숙소 예약 상태.
 *
 * <p>항공과 같은 이유로 "예약 완료"가 없다. 결제는 숙소·여행사 페이지에서 일어나고
 * 우리는 그 결과를 알 수 없다. DB 컬럼으로 저장하지 않고
 * {@code user_reported_booked}와 {@code booking_ref}에서 파생한다.
 *
 * <p>항공의 {@code BookingStatus}와 달리 {@link #NONE}이 없고 {@link #SELECTED}가 있다.
 * 항공은 구간이 0/1로 고정이라 "아직 안 고른 구간"을 표현할 값이 필요했지만,
 * 숙박은 행이 존재한다는 것 자체가 이미 골랐다는 뜻이다.
 */
public enum AccommodationBookingStatus {

    /** 여행에 담아둔 상태. 예약 사이트로 나가지 않았거나 결과를 아직 안 알렸다. */
    SELECTED,

    /** 사용자가 예약했다고 직접 표시한 것. 검증되지 않았다. */
    USER_REPORTED,

    /** 예약번호가 입력된 것. 사실상 확정으로 본다. */
    CONFIRMED
}
