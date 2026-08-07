package org.example.all_my_trip_project.domain.flight.type;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IATA ↔ TAGO 공항 코드 매핑.
 *
 * <p>TAGO는 IATA를 쓰지 않는다. {@code NAA} + ICAO 형식의 자체 코드를 쓴다.
 * {@code GetArprtList} 결과 15개를 그대로 고정한 값이며, 매 요청마다 조회하지 않는다.
 *
 * <p>DB 테이블이 아니라 enum으로 둔 이유는 15개가 전부이고 바뀌지 않기 때문이다.
 * 테이블로 두면 provider가 조회 계층에 의존하게 되고 단위 테스트에서 DB가 필요해진다.
 */
public enum DomesticAirport {

    MWX("NAARKJB", "무안"),
    KWJ("NAARKJJ", "광주"),
    KUV("NAARKJK", "군산"),
    RSU("NAARKJY", "여수"),
    WJU("NAARKNW", "원주"),
    YNY("NAARKNY", "양양"),
    CJU("NAARKPC", "제주"),
    PUS("NAARKPK", "김해"),
    HIN("NAARKPS", "사천"),
    USN("NAARKPU", "울산"),
    ICN("NAARKSI", "인천"),
    GMP("NAARKSS", "김포"),
    KPO("NAARKTH", "포항"),
    TAE("NAARKTN", "대구"),
    CJJ("NAARKTU", "청주");

    private static final Map<String, DomesticAirport> BY_TAGO_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    DomesticAirport::getTagoCode, Function.identity()));

    private final String tagoCode;
    private final String koreanName;

    DomesticAirport(String tagoCode, String koreanName) {
        this.tagoCode = tagoCode;
        this.koreanName = koreanName;
    }

    public String getTagoCode() {
        return tagoCode;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getIataCode() {
        return name();
    }

    public static Optional<DomesticAirport> ofIata(String iataCode) {
        if (iataCode == null || iataCode.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(iataCode.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<DomesticAirport> ofTagoCode(String tagoCode) {
        return Optional.ofNullable(BY_TAGO_CODE.get(tagoCode));
    }
}
