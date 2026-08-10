package org.example.all_my_trip_project.domain.accommodation.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 숙소 검색 조건.
 *
 * @param destination 지역명. "제주", "부산 해운대"
 * @param checkIn     체크인 날짜
 * @param checkOut    체크아웃 날짜
 * @param adults      성인 인원
 * @param rooms       객실 수
 * @param currency    통화 코드. "KRW"
 *
 * <p><b>항공의 {@code FlightSearchQuery}와 달리 구간(leg) 개념이 없다.</b>
 * 항공은 가는 편·오는 편 둘로 끝나지만 숙박은 박 수가 여행 일수에 따라 달라지고,
 * 도시를 옮기면 한 여행에 숙소가 여러 건 붙는다. 그래서 검색은 기간으로만 받고,
 * 여행의 어느 구간에 붙일지는 저장 단계에서 정한다(별도 이슈).
 */
public record AccommodationSearchQuery(
        String destination,
        LocalDate checkIn,
        LocalDate checkOut,
        int adults,
        int rooms,
        String currency
) {
    public static final String DEFAULT_CURRENCY = "KRW";

    public AccommodationSearchQuery {
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
        if (adults < 1) {
            adults = 1;
        }
        if (rooms < 1) {
            rooms = 1;
        }
    }

    /**
     * 박 수. 총액 계산의 기준이다.
     *
     * <p>체크아웃이 체크인보다 앞이거나 같으면 1박으로 본다.
     * 검증은 컨트롤러가 하고, 여기서는 0이나 음수가 총액 계산에 들어가지 않게만 막는다.
     * 0박이면 총액이 0원이 되어 "무료 숙소"처럼 보인다.
     */
    public int nights() {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return nights < 1 ? 1 : (int) nights;
    }
}
