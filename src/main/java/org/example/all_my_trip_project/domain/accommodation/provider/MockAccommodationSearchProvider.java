package org.example.all_my_trip_project.domain.accommodation.provider;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationProviderRole;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 목업 숙소 목록.
 *
 * <p><b>임시 구현이 아니라 영구 폴백이다.</b> 항공의 {@code MockFlightSearchProvider}와 같은 역할로,
 * 로컬 개발·테스트·실 provider 장애 시에 쓴다.
 *
 * <p>순서상 가장 뒤에 둔다. 실 provider가 붙으면 그쪽이 먼저 잡히고 이쪽은 폴백으로만 남아야 한다.
 *
 * <p>지역을 못 찾으면 빈 목록이 아니라 기본 세트를 준다. 화면과 플로우를 만드는 단계라
 * "검색했는데 아무것도 안 나옴"이 반복되면 개발이 막힌다. 실 provider가 붙으면
 * {@link #supports}가 좁아지므로 이 동작은 자연히 사라진다.
 */
@Component
@Order(Integer.MAX_VALUE)
public class MockAccommodationSearchProvider implements AccommodationSearchProvider {

    public static final String NAME = "mock";

    private static final DateTimeFormatter OFFER_ID_DATE = DateTimeFormatter.ofPattern("MMdd");

    private record MockStay(
            String slug,
            String name,
            AccommodationType type,
            String areaLabel,
            String address,
            double rating,
            int reviewCount,
            long nightlyPrice,
            List<String> amenities,
            boolean freeCancellation,
            boolean breakfastIncluded
    ) {}

    private static final List<MockStay> DEFAULT_STAYS = List.of(
            new MockStay("ocean-stay", "오션 스테이", AccommodationType.HOTEL,
                    "해변 도보 2분", "해변로 12", 4.8, 1243, 145_000L,
                    List.of("무료 Wi-Fi", "주차", "조식"), true, true),
            new MockStay("marine-residence", "마린시티 레지던스", AccommodationType.PENSION,
                    "취사 가능", "마린대로 88", 4.7, 812, 128_000L,
                    List.of("취사 가능", "세탁기", "주차"), true, false),
            new MockStay("centum-boutique", "센텀 부티크 호텔", AccommodationType.HOTEL,
                    "지하철 3분", "센텀로 5", 4.6, 2104, 112_000L,
                    List.of("무료 Wi-Fi", "피트니스"), true, false),
            new MockStay("stay-hanok", "고요 한옥스테이", AccommodationType.HANOK,
                    "구도심 중심", "한옥길 3", 4.9, 356, 168_000L,
                    List.of("전통 조식", "정원"), false, true),
            new MockStay("backpacker-house", "백패커 하우스", AccommodationType.GUESTHOUSE,
                    "터미널 5분", "역전로 21", 4.3, 987, 42_000L,
                    List.of("무료 Wi-Fi", "공용 주방"), true, false)
    );

    /**
     * 지역별 요금 배수.
     *
     * <p>어느 지역을 검색하든 같은 금액이 나오면 화면을 검증할 때 지역 필터가
     * 동작하는지 알 수 없다. 실데이터의 지역차를 대충이라도 흉내낸다.
     */
    private static final Map<String, Double> AREA_MULTIPLIER = Map.of(
            "제주", 1.15,
            "서울", 1.30,
            "부산", 1.00,
            "강릉", 1.10,
            "여수", 0.95
    );

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AccommodationProviderRole role() {
        return AccommodationProviderRole.LISTING;
    }

    @Override
    public boolean supports(AccommodationSearchQuery query) {
        return true;
    }

    @Override
    public List<AccommodationOffer> search(AccommodationSearchQuery query) {
        double multiplier = multiplierOf(query.destination());
        int nights = query.nights();

        return DEFAULT_STAYS.stream()
                .map(stay -> toOffer(stay, query, multiplier, nights))
                .toList();
    }

    private AccommodationOffer toOffer(MockStay stay, AccommodationSearchQuery query,
                                       double multiplier, int nights) {
        BigDecimal nightly = BigDecimal.valueOf(Math.round(stay.nightlyPrice() * multiplier));
        BigDecimal total = nightly
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(query.rooms()));

        return new AccommodationOffer(
                offerId(stay, query),
                NAME,
                displayName(stay, query.destination()),
                stay.type(),
                stay.areaLabel(),
                query.destination() + " " + stay.address(),
                stay.rating(),
                stay.reviewCount(),
                nightly,
                total,
                query.currency(),
                AccommodationPriceSource.MOCK,
                stay.amenities(),
                stay.freeCancellation(),
                stay.breakfastIncluded(),
                null,
                null,
                null,
                null,
                0.0
        );
    }

    /**
     * 목업 이름에 검색한 지역을 붙인다.
     *
     * <p>"오션 스테이"만 뜨면 어느 지역을 검색했는지 화면에서 구분이 안 된다.
     * 지역이 결과에 반영되고 있다는 것을 눈으로 확인하기 위한 것이다.
     */
    private String displayName(MockStay stay, String destination) {
        return stay.name() + " " + destination;
    }

    private String offerId(MockStay stay, AccommodationSearchQuery query) {
        return NAME + ":" + stay.slug() + "-" + query.checkIn().format(OFFER_ID_DATE);
    }

    /** 지역명이 "부산 해운대"처럼 들어와도 앞 토큰으로 잡히게 포함 여부로 본다. */
    private double multiplierOf(String destination) {
        if (destination == null) {
            return 1.0;
        }
        String normalized = destination.trim().toLowerCase(Locale.ROOT);
        return AREA_MULTIPLIER.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(1.0);
    }
}
