package org.example.all_my_trip_project.domain.accommodation.type;

/**
 * 숙소 형태.
 *
 * <p>국내 숙박은 형태에 따라 사용자가 기대하는 것이 크게 다르다.
 * 펜션은 취사와 인원 기준이, 게스트하우스는 도미토리 여부가 선택을 가른다.
 * 그래서 형태를 별도 필터 축으로 둔다.
 */
public enum AccommodationType {

    HOTEL("호텔"),
    RESORT("리조트"),
    PENSION("펜션"),
    GUESTHOUSE("게스트하우스"),
    MOTEL("모텔"),
    HANOK("한옥"),
    ETC("기타");

    private final String label;

    AccommodationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
