package org.example.all_my_trip_project.domain.flight.type;

/**
 * 우리가 표시하는 운임이 어디서 왔는지.
 *
 * <p>국내선 실시간 특가를 주는 무료 공식 API는 존재하지 않는다.
 * 국내 LCC가 GDS에 재고를 거의 올리지 않는 유통 구조 때문이며 기술로 우회할 수 없다.
 * 그래서 우리가 보여주는 값은 견적이 아니라 비교 힌트다.
 *
 * <p><b>출처 없는 가격은 화면에 띄우지 않는다.</b>
 */
public enum PriceSource {

    /** TAGO 공시운임. 정가이므로 실제 판매가는 더 낮을 수 있다. */
    PUBLISHED("공시운임"),

    /** Travelpayouts 캐시. "최근 이 가격에 팔린 적이 있다"는 정보이지 실시간 재고가 아니다. */
    MARKET("최근 판매가"),

    /**
     * 운임을 알 수 없는 항공편.
     *
     * <p>TAGO가 실제로 {@code economyCharge=0}인 편을 준다(예: 인천→제주 7C167).
     * 0원짜리 항공편이 아니라 운임 정보가 없는 것이므로 0으로 표시하면 거짓말이 된다.
     * 스케줄은 실재하므로 목록에서 빼지 않고, 가격 자리에만 이 사실을 밝힌다.
     */
    UNAVAILABLE("운임 미제공"),

    /** 개발·테스트용 샘플. 프로덕션 응답에 섞이면 예외를 던진다. */
    MOCK("샘플");

    private final String label;

    PriceSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 가격 비교·정렬에 참여할 수 있는 출처인지. */
    public boolean hasPrice() {
        return this != UNAVAILABLE;
    }
}
