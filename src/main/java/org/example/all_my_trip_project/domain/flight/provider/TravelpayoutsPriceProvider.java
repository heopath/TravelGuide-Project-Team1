package org.example.all_my_trip_project.domain.flight.provider;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.example.all_my_trip_project.domain.flight.type.ProviderRole;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Travelpayouts(Aviasales) Flight Data API (PRICE).
 *
 * <p><b>이걸로 항공편 목록을 만들 수 없다.</b> 실시간 재고 조회가 아니라
 * "최근 이 가격에 팔린 적이 있다"는 캐시 데이터이고, 날짜별로 가장 싼 것 위주로
 * 소수만 돌아온다. 목록은 TAGO가 만들고 이쪽은 가격만 덮어쓴다.
 *
 * <p>여기서 만드는 {@link FlightOffer}는 화면에 나가지 않는다.
 * {@code CompositeFlightSearchProvider}가 매칭 키와 가격만 꺼내 쓰는 운반용이다.
 */
@Slf4j
@Component
@Profile("!ui")
public class TravelpayoutsPriceProvider implements FlightSearchProvider {

    public static final String NAME = "travelpayouts";

    private static final String OPERATION = "/prices_for_dates";
    private static final DateTimeFormatter REQUEST_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_ROWS = 100;

    private final TravelpayoutsProperties properties;
    private final RestClient restClient;

    public TravelpayoutsPriceProvider(TravelpayoutsProperties properties,
                                      RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProviderRole role() {
        return ProviderRole.PRICE;
    }

    @Override
    public boolean supports(FlightSearchQuery query) {
        return properties.isConfigured();
    }

    @Override
    public List<FlightOffer> search(FlightSearchQuery query) {
        String body = restClient.get()
                .uri(requestUri(query))
                .header("X-Access-Token", properties.getToken())
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            return List.of();
        }
        if (!body.contains("\"success\":true")) {
            log.warn("Travelpayouts 오류 응답 route={} body={}", query.route(),
                    body.substring(0, Math.min(200, body.length())));
            return List.of();
        }
        return parse(body, query);
    }

    private URI requestUri(FlightSearchQuery query) {
        return URI.create(properties.getBaseUrl() + OPERATION
                + "?origin=" + query.origin()
                + "&destination=" + query.destination()
                + "&departure_at=" + query.departureDate().format(REQUEST_DATE)
                + "&one_way=true"
                + (query.nonStopOnly() ? "&direct=true" : "")
                + "&currency=" + query.currency().toLowerCase()
                + "&market=kr&sorting=price&limit=" + MAX_ROWS);
    }

    private List<FlightOffer> parse(String body, FlightSearchQuery query) {
        List<FlightOffer> quotes = new ArrayList<>();
        for (String item : splitObjects(body)) {
            toQuote(item, query).ifPresent(quotes::add);
        }
        return quotes;
    }

    private Optional<FlightOffer> toQuote(String item, FlightSearchQuery query) {
        String airline = jsonString(item, "airline");
        String number = jsonString(item, "flight_number");
        String departureRaw = jsonString(item, "departure_at");
        String priceRaw = jsonString(item, "price");

        if (airline == null || number == null || departureRaw == null || priceRaw == null) {
            return Optional.empty();
        }

        BigDecimal perAdult;
        LocalDateTime departureAt;
        try {
            perAdult = new BigDecimal(priceRaw);
            departureAt = OffsetDateTime.parse(departureRaw).toLocalDateTime();
        } catch (RuntimeException e) {
            return Optional.empty();
        }

        // TAGO의 vihicleId("RS907")와 같은 형태로 맞춰야 매칭 키가 일치한다.
        String flightNumber = airline + number;

        return Optional.of(new FlightOffer(
                NAME + ":" + flightNumber + "-" + departureAt.toLocalDate(),
                NAME,
                airline,
                "",
                flightNumber,
                jsonString(item, "origin_airport"),
                jsonString(item, "destination_airport"),
                departureAt,
                departureAt,
                Duration.ZERO,
                perAdult,
                perAdult.multiply(BigDecimal.valueOf(query.adults())),
                query.currency(),
                PriceSource.MARKET,
                List.of(),
                deeplink(jsonString(item, "link"))
        ));
    }

    /**
     * 응답의 {@code link}는 aviasales 상대경로다. marker를 붙여야 커미션이 추적된다.
     * marker가 없으면 링크를 만들지 않는다. 추적 안 되는 링크로 사용자를 보낼 이유가 없다.
     */
    private String deeplink(String link) {
        if (link == null || link.isBlank() || properties.getMarker() == null
                || properties.getMarker().isBlank()) {
            return null;
        }
        String separator = link.contains("?") ? "&" : "?";
        return properties.getDeeplinkBaseUrl() + link + separator + "marker=" + properties.getMarker();
    }

    /** {@code "data":[ ... ]} 안의 객체를 중괄호 단위로 자른다. */
    private List<String> splitObjects(String body) {
        int dataAt = body.indexOf("\"data\"");
        if (dataAt < 0) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = dataAt; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    items.add(body.substring(start, i + 1));
                    start = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return items;
    }

    private String jsonString(String json, String field) {
        Matcher matcher = Pattern
                .compile("\"" + field + "\"\\s*:\\s*\"?([^,\"}]*)\"?")
                .matcher(json);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.isEmpty() || "null".equals(value) ? null : value;
    }
}
