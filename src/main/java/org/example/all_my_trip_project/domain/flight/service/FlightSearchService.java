package org.example.all_my_trip_project.domain.flight.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchResponse;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchResult;
import org.example.all_my_trip_project.domain.flight.provider.CompositeFlightSearchProvider;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class FlightSearchService {

    private static final String PRODUCTION_PROFILE = "prod";

    private final CompositeFlightSearchProvider compositeProvider;
    private final Environment environment;

    /**
     * 같은 조건의 재검색은 캐시로 받는다.
     * 국내선 스케줄은 하루 단위로 거의 바뀌지 않고, TAGO는 일일 트래픽 제한이 있다.
     */
    @Cacheable(cacheNames = "flightSearch", key = "#query.toString()")
    public FlightSearchResponse search(FlightSearchQuery query) {
        FlightSearchResult result = compositeProvider.search(query);
        rejectMockInProduction(result);
        return FlightSearchResponse.from(result);
    }

    /**
     * 프로덕션에서 샘플 데이터가 조용히 나가는 것보다 500이 낫다.
     * 사용자가 가짜 운임을 보고 예약 사이트로 나가면 그 손해는 되돌릴 수 없다.
     */
    private void rejectMockInProduction(FlightSearchResult result) {
        if (!isProduction()) {
            return;
        }
        boolean hasMock = result.offers().stream()
                .map(FlightOffer::priceSource)
                .anyMatch(source -> source == PriceSource.MOCK);
        if (hasMock) {
            throw new IllegalStateException(
                    "프로덕션 응답에 MOCK 운임이 포함되었습니다. 스케줄 provider 설정을 확인하세요.");
        }
    }

    private boolean isProduction() {
        return Arrays.asList(environment.getActiveProfiles()).contains(PRODUCTION_PROFILE);
    }
}
