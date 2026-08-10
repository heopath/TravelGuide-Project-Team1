package org.example.all_my_trip_project.domain.accommodation.type;

/**
 * 숙박 요금의 출처.
 *
 * <p>항공과 같은 이유로 출처를 값과 함께 박제한다. 나중에 "이때 본 금액이
 * 정가였나 실판매가였나"를 못 밝히면 CS 대응이 불가능해진다.
 *
 * <p>항공의 {@code PriceSource}를 재사용하지 않는다. 이름은 비슷해도 의미가 다르다.
 * 항공의 PUBLISHED는 항공사 공시운임이고, 숙박의 {@link #RACK}은 숙소가 내건 정가다.
 * 한 enum으로 묶으면 어느 도메인 기준인지 읽는 쪽이 판단해야 한다.
 */
public enum AccommodationPriceSource {

    /** 제휴사가 주는 실판매가. 실시간 재고는 아니다. */
    PARTNER("실판매가"),

    /** 숙소가 내건 정가. 실제 판매가는 더 낮은 경우가 많다. */
    RACK("정가"),

    /** 요금 정보를 받지 못했다. 값 없이 출처만 남긴다. */
    UNAVAILABLE("요금 미제공"),

    /** 개발용 샘플. 프로덕션 응답에 섞이면 예외를 던진다. */
    MOCK("샘플");

    private final String label;

    AccommodationPriceSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
