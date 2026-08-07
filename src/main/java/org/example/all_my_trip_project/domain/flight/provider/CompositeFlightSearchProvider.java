package org.example.all_my_trip_project.domain.flight.provider;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchResult;
import org.example.all_my_trip_project.domain.flight.service.FlightRecommendationScorer;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.example.all_my_trip_project.domain.flight.type.ProviderRole;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SCHEDULE provider로 목록을 만들고, PRICE provider로 가격만 덮어쓴다.
 *
 * <p>가격 보강은 실패해도 된다. 공시운임으로 남을 뿐 목록은 살아 있어야 한다.
 * 사용자에게는 티내지 않고 로그만 남긴다.
 */
@Slf4j
@Component
public class CompositeFlightSearchProvider {

    private final List<FlightSearchProvider> scheduleProviders;
    private final List<FlightSearchProvider> pricingProviders;
    private final FlightRecommendationScorer scorer;
    private final TravelpayoutsProperties travelpayoutsProperties;

    public CompositeFlightSearchProvider(List<FlightSearchProvider> providers,
                                         FlightRecommendationScorer scorer,
                                         TravelpayoutsProperties travelpayoutsProperties) {
        this.scheduleProviders = providers.stream()
                .filter(p -> p.role() == ProviderRole.SCHEDULE).toList();
        this.pricingProviders = providers.stream()
                .filter(p -> p.role() == ProviderRole.PRICE).toList();
        this.scorer = scorer;
        this.travelpayoutsProperties = travelpayoutsProperties;
    }

    public FlightSearchResult search(FlightSearchQuery query) {
        FlightSearchProvider schedule = scheduleProviders.stream()
                .filter(p -> p.supports(query))
                .findFirst()
                .orElse(null);
        if (schedule == null) {
            return FlightSearchResult.empty();
        }

        List<FlightOffer> offers;
        try {
            offers = schedule.search(query);
        } catch (RuntimeException e) {
            log.warn("스케줄 조회 실패 provider={} route={} date={}",
                    schedule.name(), query.route(), query.departureDate(), e);
            return FlightSearchResult.empty();
        }
        if (offers.isEmpty()) {
            // 빈 상태 컴포넌트가 받는다. 화면이 깨지면 안 된다.
            return new FlightSearchResult(List.of(), schedule.name(), null, 0);
        }

        String priceProviderName = null;
        int matchedCount = 0;

        for (FlightSearchProvider pricing : pricingProviders) {
            if (!pricing.supports(query)) {
                continue;
            }
            try {
                List<FlightOffer> quotes = pricing.search(query);
                Merged merged = mergePrices(offers, quotes, query.adults(),
                        travelpayoutsProperties.getPriceMergeThreshold());
                offers = merged.offers();
                matchedCount += merged.matchedCount();
                priceProviderName = pricing.name();
            } catch (RuntimeException e) {
                log.warn("가격 보강 실패 provider={} — 공시운임을 유지합니다", pricing.name(), e);
            }
        }

        return new FlightSearchResult(scorer.rank(offers, query), schedule.name(),
                priceProviderName, matchedCount);
    }

    private record Merged(List<FlightOffer> offers, int matchedCount) {}

    /**
     * 매칭 키는 캐리어 + 편명 + 출발일.
     *
     * <p>매칭되는 편이 목록의 일부뿐인 게 정상이다. Travelpayouts는 캐시 기반이라
     * 날짜별로 가장 싼 것 위주의 소수만 돌려준다.
     *
     * <p><b>커버리지가 임계값에 못 미치면 가격을 덮어쓰지 않는다.</b>
     * 공시운임이 실판매가의 약 2배라, 118편 중 1편만 실판매가로 바꾸면 그 한 편이
     * 실제로 싸서가 아니라 캐시에 우연히 들어있어서 최저가 배지와 추천 1위를 가져간다.
     * 같은 기준으로 비교되지 않는 값을 나란히 놓는 순간 순위가 거짓말을 시작한다.
     *
     * <p>가격을 못 덮어써도 딥링크는 가져다 쓴다. 링크가 섞이는 것은 비교를 왜곡하지 않고,
     * 매칭된 편에서는 커미션이 추적된다.
     */
    private Merged mergePrices(List<FlightOffer> offers, List<FlightOffer> quotes,
                               int adults, double threshold) {
        Map<String, FlightOffer> quoteByKey = quotes.stream()
                .collect(Collectors.toMap(FlightOffer::matchKey, Function.identity(), (a, b) -> a, HashMap::new));

        long matchable = offers.stream()
                .filter(offer -> {
                    FlightOffer quote = quoteByKey.get(offer.matchKey());
                    return quote != null && quote.hasPrice();
                })
                .count();

        double coverage = (double) matchable / offers.size();
        boolean applyPrices = coverage >= threshold;
        if (!applyPrices && matchable > 0) {
            log.info("가격 커버리지 부족으로 공시운임을 유지합니다. matched={}/{} ({}%) threshold={}%",
                    matchable, offers.size(), Math.round(coverage * 100), Math.round(threshold * 100));
        }

        List<FlightOffer> result = new ArrayList<>(offers.size());
        int matched = 0;

        for (FlightOffer offer : offers) {
            FlightOffer quote = quoteByKey.get(offer.matchKey());
            if (quote == null || !quote.hasPrice()) {
                result.add(offer);
                continue;
            }

            FlightOffer merged = offer;
            if (applyPrices) {
                BigDecimal perAdult = quote.pricePerAdult();
                merged = merged.withPrice(perAdult,
                        perAdult.multiply(BigDecimal.valueOf(adults)), PriceSource.MARKET);
                matched++;
            }
            if (quote.deeplinkUrl() != null && !quote.deeplinkUrl().isBlank()) {
                merged = merged.withDeeplinkUrl(quote.deeplinkUrl());
            }
            result.add(merged);
        }
        return new Merged(result, matched);
    }
}
