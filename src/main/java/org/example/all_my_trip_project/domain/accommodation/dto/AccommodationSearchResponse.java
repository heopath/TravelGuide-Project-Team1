package org.example.all_my_trip_project.domain.accommodation.dto;

import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검색 API 응답.
 *
 * @param meta 어느 소스가 목록을 만들고 몇 건의 요금이 덮어써졌는지.
 *             화면에 그대로 노출하지는 않지만 하단 출처 문구와 디버깅에 쓴다.
 */
public record AccommodationSearchResponse(
        List<AccommodationOfferResponse> offers,
        Meta meta
) implements Serializable {

    /** 목록에 출처가 섞였을 때 쓰는 값. 화면 하단 문구가 이걸로 갈린다. */
    public static final String MIXED = "MIXED";

    private static final String RIBBON_RECOMMENDED = "AI 추천";
    private static final String RIBBON_CHEAPEST = "최저가";
    private static final String RIBBON_TOP_RATED = "평점 높음";

    public record Meta(
            String listingProvider,
            String priceProvider,
            int matchedPriceCount,
            int totalCount,
            int nights,
            String priceSource,
            String priceSourceNotice
    ) implements Serializable {}

    public static AccommodationSearchResponse from(AccommodationSearchResult result, int nights) {
        List<AccommodationOffer> offers = result.offers();

        String cheapestId = offers.stream()
                .filter(AccommodationOffer::hasPrice)
                .min(Comparator.comparing(AccommodationOffer::totalPrice))
                .map(AccommodationOffer::offerId)
                .orElse(null);

        String topRatedId = offers.stream()
                .filter(o -> o.rating() != null)
                .max(Comparator.comparingDouble(AccommodationOffer::rating))
                .map(AccommodationOffer::offerId)
                .orElse(null);

        String recommendedId = offers.isEmpty() ? null : offers.get(0).offerId();

        List<AccommodationOfferResponse> responses = offers.stream()
                .map(offer -> AccommodationOfferResponse.from(
                        offer, nights, ribbons(offer, recommendedId, cheapestId, topRatedId)))
                .toList();

        /*
         * 출처 요약은 값이 있는 요금만 놓고 센다.
         * 요금 미제공은 "다른 출처"가 아니라 "가격이 없음"이라,
         * 여기에 섞으면 전부 같은 출처인 목록에도 "출처가 다릅니다"가 뜬다.
         * 항공에서 같은 실수를 한 번 고쳤다.
         */
        Set<AccommodationPriceSource> pricedSources = offers.stream()
                .filter(AccommodationOffer::hasPrice)
                .map(AccommodationOffer::priceSource)
                .collect(Collectors.toUnmodifiableSet());

        long unpricedCount = offers.stream().filter(offer -> !offer.hasPrice()).count();

        String source = pricedSources.isEmpty() ? AccommodationPriceSource.UNAVAILABLE.name()
                : pricedSources.size() == 1 ? pricedSources.iterator().next().name()
                : MIXED;

        return new AccommodationSearchResponse(responses, new Meta(
                result.listingProvider(),
                result.priceProvider(),
                result.matchedPriceCount(),
                result.totalCount(),
                nights,
                responses.isEmpty() ? null : source,
                notice(responses.isEmpty(), pricedSources, source, unpricedCount)
        ));
    }

    /**
     * 추천·최저가·평점이 같은 숙소에 겹칠 수 있다. 그때는 리본을 다 붙인다.
     * 하나만 남기면 "추천인데 최저가이기도 하다"는 정보가 사라진다.
     */
    private static List<String> ribbons(AccommodationOffer offer, String recommendedId,
                                        String cheapestId, String topRatedId) {
        List<String> ribbons = new ArrayList<>();
        if (offer.offerId().equals(recommendedId)) {
            ribbons.add(RIBBON_RECOMMENDED);
        }
        if (offer.offerId().equals(cheapestId)) {
            ribbons.add(RIBBON_CHEAPEST);
        }
        if (offer.offerId().equals(topRatedId)) {
            ribbons.add(RIBBON_TOP_RATED);
        }
        return List.copyOf(ribbons);
    }

    private static String notice(boolean empty, Set<AccommodationPriceSource> pricedSources,
                                 String source, long unpricedCount) {
        if (empty) {
            return null;
        }
        String base = pricedSources.isEmpty()
                ? "이 지역은 요금 정보가 제공되지 않아 예약 사이트에서 확인해야 해요."
                : MIXED.equals(source)
                ? "요금 출처가 숙소마다 다릅니다."
                : switch (pricedSources.iterator().next()) {
                    case RACK -> "숙소가 내건 정가 기준입니다. 실제 판매가는 더 낮을 수 있어요.";
                    case PARTNER -> "제휴사 판매가 기준입니다. 실시간 재고가 아니므로 현재 요금과 다를 수 있어요.";
                    case SANDBOX -> "LiteAPI Sandbox 실습용 요금입니다. 실제 예약 가능 여부나 결제 금액이 아닙니다.";
                    case UNAVAILABLE -> "이 지역은 요금 정보가 제공되지 않아 예약 사이트에서 확인해야 해요.";
                    case MOCK -> "개발용 샘플 데이터입니다.";
                };

        if (pricedSources.isEmpty() || unpricedCount == 0) {
            return base;
        }
        return base + " 요금이 제공되지 않는 숙소 " + unpricedCount + "곳은 예약 사이트에서 확인해 주세요.";
    }
}
