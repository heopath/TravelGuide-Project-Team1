package org.example.all_my_trip_project.domain.flight.dto;

import org.example.all_my_trip_project.domain.flight.type.PriceSource;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 검색 API 응답.
 *
 * @param meta 어느 소스가 목록을 만들고 몇 건의 가격이 덮어써졌는지.
 *             화면에 그대로 노출하지는 않지만 하단 출처 문구와 디버깅에 쓴다.
 */
public record FlightSearchResponse(
        List<FlightOfferResponse> offers,
        Meta meta
) implements Serializable {

    /** 목록에 출처가 섞였을 때 쓰는 값. 화면 하단 문구가 이걸로 갈린다. */
    public static final String MIXED = "MIXED";

    public record Meta(
            String scheduleProvider,
            String priceProvider,
            int matchedPriceCount,
            int totalCount,
            String priceSource,
            String priceSourceNotice
    ) implements Serializable {}

    public static FlightSearchResponse from(FlightSearchResult result) {
        List<FlightOfferResponse> offers = result.offers().stream()
                .map(FlightOfferResponse::from)
                .toList();

        Set<PriceSource> sources = result.offers().stream()
                .map(FlightOffer::priceSource)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        String source = sources.size() == 1 ? sources.iterator().next().name() : MIXED;

        return new FlightSearchResponse(offers, new Meta(
                result.scheduleProvider(),
                result.priceProvider(),
                result.matchedPriceCount(),
                result.totalCount(),
                sources.isEmpty() ? null : source,
                notice(sources, source)
        ));
    }

    private static String notice(Set<PriceSource> sources, String source) {
        if (sources.isEmpty()) {
            return null;
        }
        if (MIXED.equals(source)) {
            return "가격 출처가 항공편마다 다릅니다.";
        }
        return switch (sources.iterator().next()) {
            case PUBLISHED -> "공시운임 기준입니다. 항공사 특가에 따라 실제 판매가는 더 낮을 수 있어요.";
            case MARKET -> "최근 판매가 기준입니다. 실시간 재고가 아니므로 현재 가격과 다를 수 있어요.";
            case UNAVAILABLE -> "이 노선은 운임 정보가 제공되지 않아 예약 사이트에서 확인해야 해요.";
            case MOCK -> "개발용 샘플 데이터입니다.";
        };
    }
}
