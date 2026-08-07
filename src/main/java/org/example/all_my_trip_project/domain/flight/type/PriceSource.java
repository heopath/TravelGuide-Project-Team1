package org.example.all_my_trip_project.domain.flight.type;

/**
 * 우리가 표시하는 운임이 어디서 왔는지.
 *
 * <p>국내선 실시간 특가를 주는 무료 공식 API는 존재하지 않는다.
 * 국내 LCC가 GDS에 재고를 거의 올리지 않는 유통 구조 때문이며 기술로 우회할 수 없다.
 * 그래서 우리가 보여주는 값은 견적이 아니라 비교 힌트다.
 *
 * <p><b>출처 없는 가격은 화면에 띄우지 않는다.</b> 이 값이 null인 offer는 렌더링 대상이 아니다.
 */
public enum PriceSource {

    /** TAGO 공시운임. 정가이므로 실제 판매가는 더 낮을 수 있다. */
    PUBLISHED("공시운임"),

    /** Travelpayouts 캐시. "최근 이 가격에 팔린 적이 있다"는 정보이지 실시간 재고가 아니다. */
    MARKET("최근 판매가"),

    /** 개발·테스트용 샘플. 프로덕션 응답에 섞이면 예외를 던진다. */
    MOCK("샘플");

    private final String label;

    PriceSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
