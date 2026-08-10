package org.example.all_my_trip_project.domain.accommodation.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchResponse;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchResult;
import org.example.all_my_trip_project.domain.accommodation.provider.CompositeAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class AccommodationSearchService {

    private static final String PRODUCTION_PROFILE = "prod";

    private final CompositeAccommodationSearchProvider compositeProvider;
    private final Environment environment;

    /**
     * 같은 조건의 재검색은 캐시로 받는다.
     *
     * <p>숙소 요금은 항공보다 자주 바뀌므로 TTL을 짧게 잡는다.
     * {@code CacheConfig}의 {@code accommodationSearch} 설정을 함께 본다.
     */
    @Cacheable(cacheNames = "accommodationSearch", key = "#query.toString()")
    public AccommodationSearchResponse search(AccommodationSearchQuery query) {
        AccommodationSearchResult result = compositeProvider.search(query);
        rejectPracticePricesInProduction(result);
        return AccommodationSearchResponse.from(result, query.nights());
    }

    /**
     * 프로덕션에서 샘플 데이터가 조용히 나가는 것보다 500이 낫다.
     * 사용자가 가짜 요금을 보고 예약 사이트로 나가면 그 손해는 되돌릴 수 없다.
     *
     * <p>TourAPI의 실제 숙소 정보는 프로덕션에서도 허용한다. 여기서 거부하는 것은
     * Mock 가격과 LiteAPI Sandbox 실습 가격뿐이다.
     */
    private void rejectPracticePricesInProduction(AccommodationSearchResult result) {
        if (!isProduction()) {
            return;
        }
        boolean hasUnsafePracticePrice = result.offers().stream()
                .map(AccommodationOffer::priceSource)
                .anyMatch(source -> source == AccommodationPriceSource.MOCK
                        || source == AccommodationPriceSource.SANDBOX);
        if (hasUnsafePracticePrice) {
            throw new IllegalStateException(
                    "프로덕션 응답에 실습용 숙소 요금이 포함되었습니다. 숙소 provider 설정을 확인하세요.");
        }
    }

    private boolean isProduction() {
        return Arrays.asList(environment.getActiveProfiles()).contains(PRODUCTION_PROFILE);
    }
}
