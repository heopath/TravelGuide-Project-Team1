package org.example.all_my_trip_project.domain.flight.provider;

import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;
import org.example.all_my_trip_project.domain.flight.service.DeeplinkBuilder;
import org.example.all_my_trip_project.domain.flight.type.DomesticAirport;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;
import org.example.all_my_trip_project.domain.flight.type.ProviderRole;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TAGO 국내항공운항정보 (SCHEDULE).
 *
 * <p>공시운임({@code economyCharge})까지 함께 주므로 스케줄과 가격을 한 번에 얻는다.
 * 다만 정가라서 실제 판매가는 더 낮을 수 있고, 그래서 {@link PriceSource#PUBLISHED}로 표시한다.
 *
 * <p>Mock보다 먼저 잡히도록 순서를 앞에 둔다.
 */
@Slf4j
@Component
// ui 프로필은 외부 연동 없이 화면만 띄우는 용도라 HTTP 클라이언트 자동설정이 빠져 있다.
// 여기에 이 빈이 남아 있으면 RestClient.Builder를 못 찾아 컨텍스트가 통째로 안 뜬다.
@Profile("!ui")
@Order(100)
public class TagoFlightSearchProvider implements FlightSearchProvider {

    public static final String NAME = "tago";

    private static final String OPERATION = "/GetFlightOpratInfoList";
    private static final DateTimeFormatter REQUEST_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter RESPONSE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final String NORMAL_RESULT_CODE = "00";

    /** 편명 앞 2자가 캐리어 코드다. "OZ8901" → "OZ" */
    private static final Pattern CARRIER_CODE = Pattern.compile("^([A-Z0-9]{2})");

    private final TagoProperties properties;
    private final DeeplinkBuilder deeplinkBuilder;
    private final RestClient restClient;

    public TagoFlightSearchProvider(TagoProperties properties,
                                    DeeplinkBuilder deeplinkBuilder,
                                    RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.deeplinkBuilder = deeplinkBuilder;
        this.restClient = restClientBuilder.build();
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
        return properties.isConfigured()
                && DomesticAirport.ofIata(query.origin()).isPresent()
                && DomesticAirport.ofIata(query.destination()).isPresent();
    }

    @Override
    public List<FlightOffer> search(FlightSearchQuery query) {
        DomesticAirport origin = DomesticAirport.ofIata(query.origin()).orElseThrow();
        DomesticAirport destination = DomesticAirport.ofIata(query.destination()).orElseThrow();

        String body = restClient.get().uri(requestUri(origin, destination, query))
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            log.warn("TAGO 응답이 비어 있습니다. route={} date={}", query.route(), query.departureDate());
            return List.of();
        }
        // _type=json을 무시하고 XML 오류 응답이 오는 경우가 있다. resultMsg라도 남겨야 원인을 안다.
        if (body.stripLeading().startsWith("<")) {
            log.warn("TAGO가 XML을 반환했습니다. route={} resultMsg={}",
                    query.route(), extractTag(body, "returnAuthMsg", "errMsg", "resultMsg"));
            return List.of();
        }

        return parse(body, query, origin, destination);
    }

    /**
     * 서비스키는 이미 인코딩된 값이라 다시 인코딩하면 안 된다.
     * {@link URI#create}는 주어진 문자열을 그대로 쓰므로 이중 인코딩이 일어나지 않는다.
     */
    private URI requestUri(DomesticAirport origin, DomesticAirport destination, FlightSearchQuery query) {
        return URI.create(properties.getBaseUrl() + OPERATION
                + "?serviceKey=" + properties.getServiceKey()
                + "&depAirportId=" + origin.getTagoCode()
                + "&arrAirportId=" + destination.getTagoCode()
                + "&depPlandTime=" + query.departureDate().format(REQUEST_DATE)
                + "&numOfRows=" + properties.getMaxRows()
                + "&pageNo=1&_type=json");
    }

    private List<FlightOffer> parse(String body, FlightSearchQuery query,
                                    DomesticAirport origin, DomesticAirport destination) {
        String resultCode = extractTag(body, "resultCode");
        if (resultCode != null && !NORMAL_RESULT_CODE.equals(resultCode)) {
            log.warn("TAGO 오류 응답 resultCode={} resultMsg={} route={}",
                    resultCode, extractTag(body, "resultMsg"), query.route());
            return List.of();
        }

        List<FlightOffer> offers = new ArrayList<>();
        for (String item : splitItems(body)) {
            toOffer(item, query, origin, destination).ifPresent(offers::add);
        }
        return offers;
    }

    private Optional<FlightOffer> toOffer(String item, FlightSearchQuery query,
                                          DomesticAirport origin, DomesticAirport destination) {
        String flightNumber = jsonString(item, "vihicleId");
        String carrierName = jsonString(item, "airlineNm");
        String departureRaw = jsonString(item, "depPlandTime");
        String arrivalRaw = jsonString(item, "arrPlandTime");

        if (flightNumber == null || departureRaw == null || arrivalRaw == null) {
            return Optional.empty();
        }

        LocalDateTime departureAt = LocalDateTime.parse(departureRaw, RESPONSE_TIME);
        LocalDateTime arrivalAt = LocalDateTime.parse(arrivalRaw, RESPONSE_TIME);

        // TAGO는 운임을 모르는 편에 0을 준다. 0원 항공편이 아니라 정보가 없는 것이다.
        long economyCharge = jsonLong(item, "economyCharge");
        boolean priced = economyCharge > 0;
        BigDecimal perAdult = priced ? BigDecimal.valueOf(economyCharge) : null;
        BigDecimal total = priced ? perAdult.multiply(BigDecimal.valueOf(query.adults())) : null;

        FlightOffer offer = new FlightOffer(
                offerId(flightNumber, departureAt),
                NAME,
                carrierCode(flightNumber),
                carrierName == null ? "" : carrierName,
                flightNumber,
                origin.getIataCode(),
                destination.getIataCode(),
                departureAt,
                arrivalAt,
                Duration.between(departureAt, arrivalAt),
                perAdult,
                total,
                query.currency(),
                priced ? PriceSource.PUBLISHED : PriceSource.UNAVAILABLE,
                List.of(),
                null
        );
        return Optional.of(offer.withDeeplinkUrl(deeplinkBuilder.build(offer, query)));
    }

    /** "tago:OZ8901-20260810" */
    private String offerId(String flightNumber, LocalDateTime departureAt) {
        return NAME + ":" + flightNumber + "-" + departureAt.toLocalDate().format(REQUEST_DATE);
    }

    private String carrierCode(String flightNumber) {
        Matcher matcher = CARRIER_CODE.matcher(flightNumber);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * item 배열을 중괄호 단위로 자른다.
     *
     * <p>Jackson으로 바인딩하지 않는 이유는 data.go.kr이 결과 0건일 때 {@code items}를
     * 객체가 아니라 빈 문자열로 주는 경우가 있어서다. 그러면 타입 바인딩이 통째로 깨진다.
     * 항목이 8개뿐인 평평한 구조라 이쪽이 더 견고하다.
     */
    private List<String> splitItems(String body) {
        int itemsAt = body.indexOf("\"item\"");
        if (itemsAt < 0) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = itemsAt; i < body.length(); i++) {
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
                } else if (depth < 0) {
                    break;
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
        return value.isEmpty() ? null : value;
    }

    private long jsonLong(String json, String field) {
        String value = jsonString(json, field);
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String extractTag(String body, String... fields) {
        for (String field : fields) {
            Matcher xml = Pattern.compile("<" + field + ">([^<]*)</" + field + ">").matcher(body);
            if (xml.find()) {
                return xml.group(1);
            }
            String json = jsonString(body, field);
            if (json != null) {
                return json;
            }
        }
        return null;
    }
}
