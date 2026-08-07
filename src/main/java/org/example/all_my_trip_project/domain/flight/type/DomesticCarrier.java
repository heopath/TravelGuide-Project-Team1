package org.example.all_my_trip_project.domain.flight.type;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ICAO ↔ IATA 항공사 코드 매핑.
 *
 * <p>TAGO의 {@code vihicleId}는 항공사마다 체계가 섞여 있다.
 * 대부분 IATA({@code 7C101}, {@code OZ8901})인데 대한항공만 ICAO({@code KAL1007})로 온다.
 * 앞 2자를 그냥 자르면 {@code KAL1007} → {@code KA}가 되는데 그건 캐세이드래곤 코드다.
 * 딥링크 템플릿도 못 찾고, Travelpayouts는 IATA로 주므로 가격 매칭도 어긋난다.
 *
 * <p>그래서 편명의 알파벳 접두어를 통째로 떼어 여기서 IATA로 정규화한다.
 * 목록에 없는 항공사는 접두어를 그대로 쓴다. 모르는 코드를 억지로 2자로 자르지 않는다.
 */
public enum DomesticCarrier {

    KE("KAL", "대한항공"),
    OZ("AAR", "아시아나항공"),
    SEVEN_C("JJA", "제주항공", "7C"),
    LJ("JNA", "진에어"),
    TW("TWB", "티웨이항공"),
    BX("ABL", "에어부산"),
    RS("ASV", "에어서울"),
    ZE("ESR", "이스타항공");

    private static final Map<String, DomesticCarrier> BY_ICAO =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    DomesticCarrier::getIcaoCode, Function.identity()));

    private static final Map<String, DomesticCarrier> BY_IATA =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    DomesticCarrier::getIataCode, Function.identity()));

    private final String icaoCode;
    private final String koreanName;
    private final String iataCode;

    DomesticCarrier(String icaoCode, String koreanName) {
        this(icaoCode, koreanName, null);
    }

    /** 숫자로 시작하는 IATA 코드(7C)는 enum 상수명으로 못 써서 따로 받는다. */
    DomesticCarrier(String icaoCode, String koreanName, String iataCode) {
        this.icaoCode = icaoCode;
        this.koreanName = koreanName;
        this.iataCode = iataCode == null ? name() : iataCode;
    }

    public String getIcaoCode() {
        return icaoCode;
    }

    public String getIataCode() {
        return iataCode;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public static Optional<DomesticCarrier> of(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String upper = code.trim().toUpperCase(Locale.ROOT);
        return Optional.ofNullable(BY_ICAO.getOrDefault(upper, BY_IATA.get(upper)));
    }

    /** 아는 항공사면 IATA로 바꾸고, 모르면 준 값을 그대로 돌려준다. */
    public static String toIata(String code) {
        return of(code).map(DomesticCarrier::getIataCode).orElse(code);
    }
}
