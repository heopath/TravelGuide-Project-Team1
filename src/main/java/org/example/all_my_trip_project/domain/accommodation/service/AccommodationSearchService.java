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
        rejectMockInProduction(result);
        return AccommodationSearchResponse.from(result, query.nights());
    }

    /**
     * 프로덕션에서 샘플 데이터가 조용히 나가는 것보다 500이 낫다.
     * 사용자가 가짜 요금을 보고 예약 사이트로 나가면 그 손해는 되돌릴 수 없다.
     *
     * <p>항공과 같은 판단이다. 다만 숙박은 현재 provider가 Mock뿐이라
     * 프로덕션에서는 이 화면이 아직 동작하지 않는다. 실 provider가 붙기 전까지는
     * 그게 맞다. 가짜 숙소를 보여주느니 비어 있는 편이 낫다.
     */
    private void rejectMockInProduction(AccommodationSearchResult result) {
        if (!isProduction()) {
            return;
        }
        boolean hasMock = result.offers().stream()
                .map(AccommodationOffer::priceSource)
                .anyMatch(source -> source == AccommodationPriceSource.MOCK);
        if (hasMock) {
            throw new IllegalStateException(
                    "프로덕션 응답에 MOCK 숙소 요금이 포함되었습니다. 숙소 provider 설정을 확인하세요.");
        }
    }

    private boolean isProduction() {
        return Arrays.asList(environment.getActiveProfiles()).contains(PRODUCTION_PROFILE);
    }
}
