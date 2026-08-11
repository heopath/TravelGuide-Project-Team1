package org.example.all_my_trip_project.domain.accommodation.type;

/**
 * 소스의 역할.
 *
 * <p>항공에서 배운 것을 그대로 가져온다. 단일 provider가 목록과 요금을 다 주지 않는다.
 * 국내 숙박은 특히 그렇다. 한국관광공사 TourAPI는 숙소 목록·주소·사진은 주지만
 * 실시간 요금이 없고, 요금을 주는 제휴사는 목록 커버리지가 좁다.
 *
 * <p>그래서 처음부터 역할을 나눠 둔다. 실 provider가 붙을 때 화면을 손대지 않기 위해서다.
 */
public enum AccommodationProviderRole {

    /** 숙소 목록을 만든다. TourAPI, Mock. */
    LISTING,

    /** 이미 만들어진 목록의 요금을 덮어쓴다. 제휴사. */
    PRICE
}
