package org.example.all_my_trip_project.domain.flight.provider;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.service.DeeplinkBuilder;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.example.all_my_trip_project.domain.flight.type.ProviderRole;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 국내선 목업 스케줄.
 *
 * <p><b>임시 구현이 아니라 영구 폴백이다.</b> 로컬 개발, 테스트, TAGO 장애 시에 쓴다.
 *
 * <p>순서상 가장 뒤에 둔다. TAGO가 붙으면 실데이터가 먼저 잡히고 이쪽은 폴백으로만 남아야 한다.
 *
 * <p>수록된 편은 모두 직항이라 {@code nonStopOnly}로 걸러낼 대상이 없다.
 * 배지는 붙이지 않는다. 일정 충돌 배지는 조회 이후에 서버가 계산한다.
 */
@Component
@Order(Integer.MAX_VALUE)
public class MockFlightSearchProvider implements FlightSearchProvider {

    public static final String NAME = "mock";

    private static final DateTimeFormatter OFFER_ID_DATE = DateTimeFormatter.ofPattern("MMdd");

    private record MockFlight(
            String carrierCode,
            String carrierName,
            String flightNumber,
            LocalTime departure,
            LocalTime arrival,
            long pricePerAdult
    ) {}

    private static final Map<String, List<MockFlight>> ROUTES = Map.of(
            "GMP-CJU", List.of(
                    new MockFlight("KE", "대한항공", "KE121", LocalTime.of(8, 10), LocalTime.of(9, 20), 89_000L),
                    new MockFlight("7C", "제주항공", "7C101", LocalTime.of(10, 30), LocalTime.of(11, 40), 76_000L),
                    new MockFlight("LJ", "진에어", "LJ301", LocalTime.of(14, 0), LocalTime.of(15, 10), 82_000L)
            ),
            "CJU-GMP", List.of(
                    new MockFlight("KE", "대한항공", "KE1284", LocalTime.of(18, 40), LocalTime.of(19, 55), 94_000L),
                    new MockFlight("7C", "제주항공", "7C122", LocalTime.of(20, 15), LocalTime.of(21, 30), 71_000L),
                    new MockFlight("TW", "티웨이항공", "TW716", LocalTime.of(15, 20), LocalTime.of(16, 35), 68_000L)
            )
    );

    private final DeeplinkBuilder deeplinkBuilder;

    public MockFlightSearchProvider(DeeplinkBuilder deeplinkBuilder) {
        this.deeplinkBuilder = deeplinkBuilder;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProviderRole role() {
        return ProviderRole.SCHEDULE;
    }

    @Override
    public boolean supports(FlightSearchQuery query) {
        return ROUTES.containsKey(query.route());
    }

    @Override
    public List<FlightOffer> search(FlightSearchQuery query) {
        List<MockFlight> flights = ROUTES.get(query.route());
        if (flights == null) {
            return List.of();
        }
        return flights.stream().map(flight -> toOffer(flight, query)).toList();
    }

    private FlightOffer toOffer(MockFlight flight, FlightSearchQuery query) {
        LocalDateTime departureAt = LocalDateTime.of(query.departureDate(), flight.departure());
        LocalDateTime arrivalAt = LocalDateTime.of(query.departureDate(), flight.arrival());
        BigDecimal pricePerAdult = BigDecimal.valueOf(flight.pricePerAdult());

        FlightOffer offer = new FlightOffer(
                offerId(flight, query),
                NAME,
                flight.carrierCode(),
                flight.carrierName(),
                flight.flightNumber(),
                query.origin(),
                query.destination(),
                departureAt,
                arrivalAt,
                Duration.between(departureAt, arrivalAt),
                pricePerAdult,
                pricePerAdult.multiply(BigDecimal.valueOf(query.adults())),
                query.currency(),
                PriceSource.MOCK,
                List.of(),
                null
        );
        return offer.withDeeplinkUrl(deeplinkBuilder.build(offer, query));
    }

    /** "mock:ke121-0815" */
    private String offerId(MockFlight flight, FlightSearchQuery query) {
        return NAME + ":" + flight.flightNumber().toLowerCase(Locale.ROOT)
                + "-" + query.departureDate().format(OFFER_ID_DATE);
    }
}
