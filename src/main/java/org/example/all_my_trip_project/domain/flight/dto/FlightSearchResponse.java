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

        /*
         * 출처 요약은 값이 있는 운임만 놓고 센다.
         * 운임 미제공은 "다른 출처"가 아니라 "가격이 없음"이라,
         * 여기에 섞으면 전부 공시운임인 목록에도 "출처가 다릅니다"가 뜬다.
         */
        Set<PriceSource> pricedSources = result.offers().stream()
                .filter(FlightOffer::hasPrice)
                .map(FlightOffer::priceSource)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        long unpricedCount = result.offers().stream().filter(offer -> !offer.hasPrice()).count();

        String source = pricedSources.isEmpty() ? PriceSource.UNAVAILABLE.name()
                : pricedSources.size() == 1 ? pricedSources.iterator().next().name()
                : MIXED;

        return new FlightSearchResponse(offers, new Meta(
                result.scheduleProvider(),
                result.priceProvider(),
                result.matchedPriceCount(),
                result.totalCount(),
                offers.isEmpty() ? null : source,
                notice(offers.isEmpty(), pricedSources, source, unpricedCount)
        ));
    }

    private static String notice(boolean empty, Set<PriceSource> pricedSources,
                                 String source, long unpricedCount) {
        if (empty) {
            return null;
        }
        String base = pricedSources.isEmpty()
                ? "이 노선은 운임 정보가 제공되지 않아 예약 사이트에서 확인해야 해요."
                : MIXED.equals(source)
                ? "가격 출처가 항공편마다 다릅니다."
                : switch (pricedSources.iterator().next()) {
                    case PUBLISHED -> "공시운임 기준입니다. 항공사 특가에 따라 실제 판매가는 더 낮을 수 있어요.";
                    case MARKET -> "최근 판매가 기준입니다. 실시간 재고가 아니므로 현재 가격과 다를 수 있어요.";
                    case UNAVAILABLE -> "이 노선은 운임 정보가 제공되지 않아 예약 사이트에서 확인해야 해요.";
                    case MOCK -> "개발용 샘플 데이터입니다.";
                };

        if (pricedSources.isEmpty() || unpricedCount == 0) {
            return base;
        }
        return base + " 운임이 제공되지 않는 항공편 " + unpricedCount + "편은 예약 사이트에서 확인해 주세요.";
    }
}
