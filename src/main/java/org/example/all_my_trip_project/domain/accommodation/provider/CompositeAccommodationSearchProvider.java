package org.example.all_my_trip_project.domain.accommodation.provider;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchResult;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationRecommendationScorer;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationProviderRole;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LISTING provider로 목록을 만들고, PRICE provider로 요금만 덮어쓴다.
 *
 * <p>요금 보강은 실패해도 된다. 정가로 남을 뿐 목록은 살아 있어야 한다.
 * 사용자에게는 티내지 않고 로그만 남긴다. 항공에서 쓴 방식 그대로다.
 */
@Slf4j
@Component
public class CompositeAccommodationSearchProvider {

    private final List<AccommodationSearchProvider> listingProviders;
    private final List<AccommodationSearchProvider> pricingProviders;
    private final AccommodationRecommendationScorer scorer;

    public CompositeAccommodationSearchProvider(List<AccommodationSearchProvider> providers,
                                                AccommodationRecommendationScorer scorer) {
        this.listingProviders = providers.stream()
                .filter(p -> p.role() == AccommodationProviderRole.LISTING).toList();
        this.pricingProviders = providers.stream()
                .filter(p -> p.role() == AccommodationProviderRole.PRICE).toList();
        this.scorer = scorer;
    }

    public AccommodationSearchResult search(AccommodationSearchQuery query) {
        AccommodationSearchProvider listing = listingProviders.stream()
                .filter(p -> p.supports(query))
                .findFirst()
                .orElse(null);

        if (listing == null) {
            log.warn("숙소 목록을 만들 provider가 없습니다. destination={}", query.destination());
            return AccommodationSearchResult.empty();
        }

        List<AccommodationOffer> offers = listing.search(query);
        if (offers.isEmpty()) {
            return AccommodationSearchResult.empty();
        }

        Priced priced = applyPrices(offers, query);
        List<AccommodationOffer> scored = scorer.score(priced.offers());

        return new AccommodationSearchResult(
                sortByScore(scored),
                listing.name(),
                priced.providerName(),
                priced.matchedCount()
        );
    }

    private record Priced(List<AccommodationOffer> offers, String providerName, int matchedCount) {}

    private Priced applyPrices(List<AccommodationOffer> offers, AccommodationSearchQuery query) {
        AccommodationSearchProvider pricing = pricingProviders.stream()
                .filter(p -> p.supports(query))
                .findFirst()
                .orElse(null);

        if (pricing == null) {
            return new Priced(offers, null, 0);
        }

        List<AccommodationOffer> quotes;
        try {
            quotes = pricing.search(query);
        } catch (RuntimeException e) {
            /* 요금 보강 실패로 목록까지 잃지 않는다. 사용자에게는 정가가 보인다. */
            log.warn("숙소 요금 보강에 실패해 목록만 반환합니다. provider={}", pricing.name(), e);
            return new Priced(offers, null, 0);
        }

        Map<String, AccommodationOffer> byId = quotes.stream()
                .filter(AccommodationOffer::hasPrice)
                .collect(Collectors.toMap(AccommodationOffer::offerId, Function.identity(),
                        (first, second) -> first));

        int[] matched = {0};
        List<AccommodationOffer> merged = offers.stream()
                .map(offer -> {
                    AccommodationOffer quote = byId.get(offer.offerId());
                    if (quote == null) {
                        return offer;
                    }
                    matched[0]++;
                    return offer.withPrice(quote.nightlyPrice(), quote.totalPrice(), quote.priceSource());
                })
                .toList();

        return new Priced(merged, pricing.name(), matched[0]);
    }

    /**
     * 추천순이 기본 정렬이다. 최저가순·평점순은 화면에서 다시 정렬한다.
     *
     * <p>점수가 같으면 싼 쪽을 앞에 둔다. 동점일 때 순서가 매 호출마다 달라지면
     * 캐시 적중 여부에 따라 목록이 흔들리는 것처럼 보인다.
     */
    private List<AccommodationOffer> sortByScore(List<AccommodationOffer> offers) {
        return offers.stream()
                .sorted(Comparator.comparingDouble(AccommodationOffer::score).reversed()
                        .thenComparing(offer -> offer.hasPrice() ? offer.totalPrice() : null,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AccommodationOffer::offerId))
                .toList();
    }
}
