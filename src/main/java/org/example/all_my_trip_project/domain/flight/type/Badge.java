package org.example.all_my_trip_project.domain.flight.type;

/**
 * 항공편 카드에 붙는 배지. <b>전부 우리가 계산한다.</b>
 *
 * <p>수하물·운임규정 배지는 만들지 않는다. TAGO도 Travelpayouts도 그 정보를 주지 않고,
 * 같은 항공사라도 운임 등급마다 다르다. 확인되지 않은 정보를 단정하면 사용자가 손해를 본다.
 *
 * <p>직항 배지도 없다. 검색 필터 조건이라 카드에 다시 표시하면 같은 사실이 두 번 나온다.
 *
 * <p>일정 충돌 배지는 사용자 일정을 아는 우리만 붙일 수 있다. 이게 차별점이다.
 */
public enum Badge {

    AI_PICK("AI 추천", Placement.RIBBON, ""),
    LOWEST_PRICE("최저가", Placement.RIBBON, ""),

    LATE_FOR_FIRST_PLAN("첫 일정 늦음", Placement.CARD, "w"),
    MISSES_LAST_PLAN("마지막 일정 못 함", Placement.CARD, "w"),
    EARLY_DEPARTURE("이른 출발", Placement.CARD, "");

    /** 리본은 카드 상단, 배지는 항공편 정보 아래. 각각 최대 2개까지만 노출한다. */
    public enum Placement { RIBBON, CARD }

    private final String label;
    private final Placement placement;

    /** 화면 강조 색상 구분자. 빈 문자열은 기본, {@code w}는 주의. */
    private final String tone;

    Badge(String label, Placement placement, String tone) {
        this.label = label;
        this.placement = placement;
        this.tone = tone;
    }

    public String getLabel() {
        return label;
    }

    public Placement getPlacement() {
        return placement;
    }

    public String getTone() {
        return tone;
    }
}
