package org.example.all_my_trip_project.domain.accommodation.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationPriceResult;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LiteAPI Sandbox의 숙박 요금과 취소 조건을 TourAPI 목록에 보강한다.
 *
 * <p>두 서비스의 숙소 ID는 서로 다르다. 한 번의 위치 기반 조회 후 이름과 좌표가
 * 충분히 가까운 경우에만 연결한다. 애매한 결과는 잘못된 금액보다 미제공 상태가 안전하다.
 * Sandbox 키와 응답의 {@code sandbox=true}를 모두 확인하며 prod 프로필에서는 호출하지 않는다.
 */
@Slf4j
@Component
@Profile("!ui")
@Order(100)
public class LiteApiSandboxPriceProvider implements AccommodationPriceProvider {

    public static final String NAME = "liteapi-sandbox";

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final int MIN_RADIUS_METERS = 1_000;

    private final LiteApiSandboxProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    private record Point(double latitude, double longitude) {}
    private record LiteHotel(String id, String name, String address, Double latitude, Double longitude) {}
    private record LiteQuote(String hotelId, String name, String address,
                             Double latitude, Double longitude, BigDecimal total,
                             String currency, boolean refundable, boolean breakfast) {}
    private record RateDetails(BigDecimal total, String currency,
                               boolean refundable, boolean breakfast) {}

    public LiteApiSandboxPriceProvider(LiteApiSandboxProperties properties,
                                       RestClient.Builder restClientBuilder,
                                       ObjectMapper objectMapper,
                                       Environment environment) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(AccommodationSearchQuery query, List<AccommodationOffer> offers) {
        return !environment.acceptsProfiles(Profiles.of("prod"))
                && properties.hasSandboxKey()
                && offers.stream().anyMatch(this::hasCoordinates);
    }

    @Override
    public AccommodationPriceResult apply(List<AccommodationOffer> offers,
                                          AccommodationSearchQuery query) {
        if (!supports(query, offers)) {
            return AccommodationPriceResult.unchanged(offers);
        }

        try {
            Point center = centerOf(offers);
            int radius = radiusOf(center, offers);
            String responseBody = requestRates(query, center, radius);
            JsonNode root = responseBody == null || responseBody.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(responseBody);

            // Sandbox 전용 기능이므로 운영 응답이 오면 가격을 한 건도 사용하지 않는다.
            if (!root.path("sandbox").asBoolean(false)) {
                log.error("LiteAPI Sandbox provider가 sandbox가 아닌 응답을 거부했습니다.");
                return AccommodationPriceResult.unchanged(offers);
            }

            List<LiteQuote> quotes = quotes(root);
            if (quotes.isEmpty()) {
                return AccommodationPriceResult.unchanged(offers);
            }
            return merge(offers, quotes, query);
        } catch (RestClientResponseException exception) {
            log.warn("LiteAPI Sandbox 요금 조회 HTTP 오류 status={}",
                    exception.getStatusCode().value());
            return AccommodationPriceResult.unchanged(offers);
        } catch (RuntimeException exception) {
            // 예외 메시지나 요청 URL에는 키가 포함될 가능성이 있어 타입만 기록한다.
            log.warn("LiteAPI Sandbox 요금 조회 실패 type={}",
                    exception.getClass().getSimpleName());
            return AccommodationPriceResult.unchanged(offers);
        } catch (Exception exception) {
            log.warn("LiteAPI Sandbox 요금 응답 해석 실패 type={}",
                    exception.getClass().getSimpleName());
            return AccommodationPriceResult.unchanged(offers);
        }
    }

    private String requestRates(AccommodationSearchQuery query, Point center, int radius) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("occupancies", occupancies(query.adults(), query.rooms()));
        body.put("guestNationality", properties.getGuestNationality());
        body.put("currency", query.currency());
        body.put("checkin", query.checkIn().toString());
        body.put("checkout", query.checkOut().toString());
        body.put("latitude", center.latitude());
        body.put("longitude", center.longitude());
        body.put("radius", radius);
        body.put("limit", properties.getMaxHotels());
        body.put("roomMapping", true);
        body.put("includeHotelData", true);
        body.put("maxRatesPerHotel", 1);
        body.put("timeout", properties.getTimeoutSeconds());

        return restClient.post()
                .uri(properties.getBaseUrl() + "/hotels/rates?rm=true")
                .header("X-API-Key", properties.getApiKey().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private List<Map<String, Integer>> occupancies(int adults, int rooms) {
        int roomCount = Math.max(1, Math.min(rooms, adults));
        int remainingAdults = Math.max(1, adults);
        List<Map<String, Integer>> result = new ArrayList<>(roomCount);
        for (int index = 0; index < roomCount; index++) {
            int remainingRooms = roomCount - index;
            int roomAdults = (int) Math.ceil((double) remainingAdults / remainingRooms);
            result.add(Map.of("adults", roomAdults));
            remainingAdults -= roomAdults;
        }
        return result;
    }

    private Point centerOf(List<AccommodationOffer> offers) {
        List<AccommodationOffer> located = offers.stream().filter(this::hasCoordinates).toList();
        double latitude = located.stream().mapToDouble(AccommodationOffer::latitude).average().orElseThrow();
        double longitude = located.stream().mapToDouble(AccommodationOffer::longitude).average().orElseThrow();
        return new Point(latitude, longitude);
    }

    private int radiusOf(Point center, List<AccommodationOffer> offers) {
        double farthest = offers.stream().filter(this::hasCoordinates)
                .mapToDouble(offer -> distance(center.latitude(), center.longitude(),
                        offer.latitude(), offer.longitude()))
                .max().orElse(0.0);
        return (int) Math.min(properties.getMaxRadiusMeters(),
                Math.max(MIN_RADIUS_METERS, Math.ceil(farthest + 2_000)));
    }

    private boolean hasCoordinates(AccommodationOffer offer) {
        return offer.latitude() != null && offer.longitude() != null;
    }

    private List<LiteQuote> quotes(JsonNode root) {
        Map<String, LiteHotel> hotels = hotels(root.path("hotels"));
        List<LiteQuote> result = new ArrayList<>();

        for (JsonNode data : iterable(root.path("data"))) {
            String hotelId = firstText(data, "hotelId", "id");
            if (hotelId.isBlank()) continue;

            LiteHotel hotel = hotels.get(hotelId);
            if (hotel == null && data.path("hotel").isObject()) {
                hotel = hotel(data.path("hotel"));
            }
            if (hotel == null) {
                hotel = new LiteHotel(hotelId, "", "", null, null);
            }

            RateDetails cheapest = cheapest(data.path("roomTypes"));
            if (cheapest == null) continue;
            result.add(new LiteQuote(hotelId, hotel.name(), hotel.address(),
                    hotel.latitude(), hotel.longitude(), cheapest.total(), cheapest.currency(),
                    cheapest.refundable(), cheapest.breakfast()));
        }
        return result;
    }

    private Map<String, LiteHotel> hotels(JsonNode node) {
        Map<String, LiteHotel> result = new HashMap<>();
        for (JsonNode item : iterable(node)) {
            LiteHotel hotel = hotel(item);
            if (!hotel.id().isBlank()) result.put(hotel.id(), hotel);
        }
        return result;
    }

    private LiteHotel hotel(JsonNode item) {
        JsonNode location = item.path("location");
        Double latitude = firstNumber(location, "latitude", "lat");
        Double longitude = firstNumber(location, "longitude", "lng", "lon");
        if (latitude == null) latitude = firstNumber(item, "latitude", "lat");
        if (longitude == null) longitude = firstNumber(item, "longitude", "lng", "lon");
        return new LiteHotel(firstText(item, "id", "hotelId"),
                firstText(item, "name", "hotelName"), firstText(item, "address", "formattedAddress"),
                latitude, longitude);
    }

    private RateDetails cheapest(JsonNode roomTypes) {
        RateDetails cheapest = null;
        for (JsonNode roomType : iterable(roomTypes)) {
            RateDetails current = rateDetails(roomType);
            if (current != null && (cheapest == null
                    || current.total().compareTo(cheapest.total()) < 0)) {
                cheapest = current;
            }
        }
        return cheapest;
    }

    private RateDetails rateDetails(JsonNode roomType) {
        List<JsonNode> rates = iterable(roomType.path("rates"));
        BigDecimal combinedAmount = decimal(roomType.path("offerRetailRate").path("amount"));
        String combinedCurrency = text(roomType.path("offerRetailRate"), "currency");
        BigDecimal sum = BigDecimal.ZERO;
        String currency = combinedCurrency;
        boolean refundable = !rates.isEmpty();
        boolean breakfast = !rates.isEmpty();

        for (JsonNode rate : rates) {
            JsonNode total = firstArrayItem(rate.path("retailRate").path("total"));
            BigDecimal amount = decimal(total.path("amount"));
            if (amount != null) sum = sum.add(amount);
            if (currency.isBlank()) currency = text(total, "currency");

            String refundableTag = text(rate.path("cancellationPolicies"), "refundableTag");
            refundable &= "RFN".equalsIgnoreCase(refundableTag);
            String boardType = text(rate, "boardType").toUpperCase(Locale.ROOT);
            String boardName = text(rate, "boardName").toLowerCase(Locale.ROOT);
            breakfast &= boardType.startsWith("BB") || boardName.contains("breakfast");
        }

        BigDecimal total = combinedAmount != null ? combinedAmount : sum;
        if (total.signum() <= 0 || currency.isBlank()) return null;
        return new RateDetails(total, currency, refundable, breakfast);
    }

    private AccommodationPriceResult merge(List<AccommodationOffer> offers,
                                            List<LiteQuote> quotes,
                                            AccommodationSearchQuery query) {
        Set<String> usedHotelIds = new HashSet<>();
        int[] matched = {0};
        List<AccommodationOffer> merged = offers.stream().map(offer -> {
            LiteQuote quote = bestMatch(offer, quotes, usedHotelIds);
            if (quote == null) return offer;
            usedHotelIds.add(quote.hotelId());
            matched[0]++;
            return offer.withRate(quote.total(), quote.currency(), AccommodationPriceSource.SANDBOX,
                    query.nights(), Math.min(query.rooms(), query.adults()),
                    quote.refundable(), quote.breakfast());
        }).toList();
        return new AccommodationPriceResult(merged, matched[0]);
    }

    private LiteQuote bestMatch(AccommodationOffer offer, List<LiteQuote> quotes, Set<String> used) {
        LiteQuote best = null;
        double bestScore = -1;
        for (LiteQuote quote : quotes) {
            if (used.contains(quote.hotelId())) continue;
            double score = matchScore(offer, quote);
            if (score > bestScore) {
                best = quote;
                bestScore = score;
            }
        }
        return bestScore >= 0 ? best : null;
    }

    private double matchScore(AccommodationOffer offer, LiteQuote quote) {
        String offerName = normalize(offer.name());
        String quoteName = normalize(quote.name());
        double nameSimilarity = similarity(offerName, quoteName);
        boolean namesEquivalent = !offerName.isBlank() && !quoteName.isBlank()
                && (offerName.equals(quoteName) || offerName.contains(quoteName) || quoteName.contains(offerName));

        Double distance = null;
        if (hasCoordinates(offer) && quote.latitude() != null && quote.longitude() != null) {
            distance = distance(offer.latitude(), offer.longitude(), quote.latitude(), quote.longitude());
        }

        if (namesEquivalent && (distance == null || distance <= 5_000)) {
            return 10_000 + nameSimilarity * 1_000 - (distance == null ? 0 : distance);
        }
        // 이름 언어가 달라도 두 공급자의 좌표가 사실상 같으면 같은 숙소로 본다.
        if (distance != null && distance <= 50) {
            return 8_000 + nameSimilarity * 1_000 - distance;
        }
        if (distance != null && distance <= properties.getMatchDistanceMeters() && nameSimilarity >= 0.35) {
            return 5_000 + nameSimilarity * 1_000 - distance;
        }
        return -1;
    }

    private double similarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) return 0.0;
        if (left.equals(right)) return 1.0;
        Set<String> leftPairs = pairs(left);
        Set<String> rightPairs = pairs(right);
        if (leftPairs.isEmpty() || rightPairs.isEmpty()) return 0.0;
        long common = leftPairs.stream().filter(rightPairs::contains).count();
        return (2.0 * common) / (leftPairs.size() + rightPairs.size());
    }

    private Set<String> pairs(String value) {
        Set<String> result = new HashSet<>();
        for (int index = 0; index < value.length() - 1; index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private double distance(double latitude1, double longitude1,
                            double latitude2, double longitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double deltaLat = Math.toRadians(latitude2 - latitude1);
        double deltaLon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<JsonNode> iterable(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return result;
    }

    private JsonNode firstArrayItem(JsonNode node) {
        return node.isArray() && !node.isEmpty() ? node.get(0) : objectMapper.createObjectNode();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private Double firstNumber(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) return value.doubleValue();
            if (value.isTextual()) {
                try { return Double.valueOf(value.asText()); }
                catch (NumberFormatException ignored) { /* 다음 이름을 확인한다 */ }
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            if (node.isNumber()) return node.decimalValue();
            if (node.isTextual() && !node.asText().isBlank()) return new BigDecimal(node.asText());
            return null;
        }
        catch (RuntimeException exception) { return null; }
    }
}
