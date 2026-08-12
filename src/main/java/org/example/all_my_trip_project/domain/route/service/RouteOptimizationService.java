package org.example.all_my_trip_project.domain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.example.all_my_trip_project.domain.route.dto.RouteOptimizationResponse;
import org.example.all_my_trip_project.domain.route.dto.RouteOptimizationResponse.RouteSegment;
import org.example.all_my_trip_project.domain.route.dto.TransitRouteRequest;
import org.example.all_my_trip_project.domain.route.dto.TransitRouteResponse;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {
    private final TripService tripService;
    private final PlaceService placeService;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.rest-api-key:}")
    private String restApiKey;

    public TransitRouteResponse searchTransitRoute(TransitRouteRequest request) {
        JsonNode response = requestKakaoMapRoute("publictraffic", request, null);
        ensureKakaoMapRouteAvailable(response, "publictraffic");
        return parseTransitResponse(response, request);
    }

    private TransitRouteResponse parseTransitResponse(JsonNode response, TransitRouteRequest request) {
        JsonNode route = response.path("routes").path(0);
        if (!route.isObject()) throw new BusinessException(ErrorCode.ROUTE_API_FAILED);

        JsonNode properties = route.path("properties");
        List<TransitRouteResponse.TransitSection> sections = new ArrayList<>();
        List<TransitRouteResponse.RoutePoint> points = new ArrayList<>();
        addPoint(points, request.startX(), request.startY());

        int totalWalkMeters = 0;
        JsonNode steps = route.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                JsonNode stepProperties = step.path("properties");
                String type = stepProperties.path("type").asText("WALKING");
                String mode = switch (type) {
                    case "BUS" -> "버스";
                    case "SUBWAY" -> "지하철";
                    default -> "도보";
                };
                int durationSeconds = stepProperties.path("time").asInt(0);
                int distanceMeters = stepProperties.path("distance").asInt(0);
                if ("WALKING".equals(type)) totalWalkMeters += distanceMeters;

                JsonNode stops = stepProperties.path("stops");
                String fromName = stops.isArray() && !stops.isEmpty()
                        ? stops.path(0).path("name").asText("출발")
                        : "도보 이동";
                String toName = stops.isArray() && !stops.isEmpty()
                        ? stops.path(stops.size() - 1).path("name").asText("도착")
                        : "도보 이동";
                String routeName = stepProperties.path("vehicles").path(0).path("name").asText("");
                sections.add(new TransitRouteResponse.TransitSection(
                        mode,
                        fromName,
                        toName,
                        routeName,
                        durationSeconds,
                        distanceMeters));
                addPathPoints(points, step.path("path").path("points"));
            }
        }
        addPoint(points, request.endX(), request.endY());

        return new TransitRouteResponse(
                properties.path("totalTime").asInt(0),
                properties.path("totalDistance").asInt(0),
                totalWalkMeters,
                properties.path("transfers").asInt(0),
                properties.path("fare").path("value").asInt(0),
                sections,
                points);
    }

    public TransitRouteResponse searchWalkingRoute(TransitRouteRequest request) {
        JsonNode response = requestKakaoMapRoute("walk", request, "SHORTEST");
        ensureKakaoMapRouteAvailable(response, "walk");
        JsonNode route = response.path("route");
        if (!route.isObject()) throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND);

        JsonNode properties = route.path("properties");
        int totalDurationSeconds = properties.path("totalTime").asInt(0);
        int totalDistanceMeters = properties.path("totalDistance").asInt(0);
        List<TransitRouteResponse.RoutePoint> points = new ArrayList<>();
        addPoint(points, request.startX(), request.startY());
        JsonNode legs = route.path("legs");
        if (legs.isArray()) {
            for (JsonNode leg : legs) {
                JsonNode steps = leg.path("steps");
                if (!steps.isArray()) continue;
                for (JsonNode step : steps) {
                    addPathPoints(points, step.path("path").path("points"));
                }
            }
        }
        addPoint(points, request.endX(), request.endY());

        List<TransitRouteResponse.TransitSection> sections = List.of(
                new TransitRouteResponse.TransitSection(
                        "도보", "출발", "도착", "",
                        totalDurationSeconds, totalDistanceMeters));
        return new TransitRouteResponse(
                totalDurationSeconds,
                totalDistanceMeters,
                totalDistanceMeters,
                0,
                0,
                sections,
                points);
    }

    public TransitRouteResponse searchDrivingRoute(TransitRouteRequest request) {
        String kakaoKey = restApiKey == null ? "" : restApiKey.trim();
        if (kakaoKey.isBlank()) throw new BusinessException(ErrorCode.ROUTE_NOT_CONFIGURED);

        String url = UriComponentsBuilder
                .fromUriString("https://apis-navi.kakaomobility.com/v1/directions")
                .queryParam("origin", request.startX() + "," + request.startY())
                .queryParam("destination", request.endX() + "," + request.endY())
                .queryParam("priority", "TIME")
                .queryParam("summary", "false")
                .build()
                .toUriString();
        try {
            String json = RestClient.create().get().uri(url)
                    .header("Authorization", "KakaoAK " + kakaoKey)
                    .header("Content-Type", "application/json")
                    .retrieve().body(String.class);
            JsonNode route = objectMapper.readTree(json).path("routes").path(0);
            if (!route.isObject()) throw new BusinessException(ErrorCode.ROUTE_API_FAILED);
            if (route.path("result_code").asInt(-1) != 0) {
                log.info("Kakao Mobility driving route was not found: code={}",
                        route.path("result_code").asInt(-1));
                throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND);
            }

            JsonNode summary = route.path("summary");
            int durationSeconds = summary.path("duration").asInt(0);
            int distanceMeters = summary.path("distance").asInt(0);
            List<TransitRouteResponse.RoutePoint> points = new ArrayList<>();
            addPoint(points, request.startX(), request.startY());
            JsonNode sections = route.path("sections");
            if (sections.isArray()) {
                for (JsonNode section : sections) {
                    JsonNode roads = section.path("roads");
                    if (!roads.isArray()) continue;
                    for (JsonNode road : roads) addVertexPoints(points, road.path("vertexes"));
                }
            }
            addPoint(points, request.endX(), request.endY());

            return new TransitRouteResponse(
                    durationSeconds,
                    distanceMeters,
                    0,
                    0,
                    0,
                    List.of(new TransitRouteResponse.TransitSection(
                            "자동차", "출발", "도착", "",
                            durationSeconds, distanceMeters)),
                    points);
        } catch (RestClientResponseException error) {
            log.warn("Kakao Mobility driving route rejected request: status={}, body={}",
                    error.getStatusCode().value(), error.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ROUTE_API_FAILED);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            log.warn("Kakao Mobility driving route request failed", error);
            throw new BusinessException(ErrorCode.ROUTE_API_FAILED);
        }
    }

    private void ensureKakaoMapRouteAvailable(JsonNode response, String routeType) {
        String status = response.path("status").asText("UNKNOWN");
        if ("OK".equals(status)) return;

        boolean routeNotFound = switch (status) {
            case "STARTNODES_NULL", "ENDNODES_NULL", "EQUAL_POINTS", "NO_RESULTS",
                    "SAME_POINT", "START_LINK_NOT_FOUND", "END_LINK_NOT_FOUND",
                    "TOO_MANY_SEARCH_LINK", "TOO_FAR_AWAY", "ROUTE_RESULT_NOT_FOUND" -> true;
            default -> false;
        };
        log.info("Kakao map {} route returned status={}", routeType, status);
        throw new BusinessException(routeNotFound ? ErrorCode.ROUTE_NOT_FOUND : ErrorCode.ROUTE_API_FAILED);
    }

    private JsonNode requestKakaoMapRoute(
            String routeType,
            TransitRouteRequest request,
            String routeMode) {
        String kakaoKey = restApiKey == null ? "" : restApiKey.trim();
        if (kakaoKey.isBlank()) throw new BusinessException(ErrorCode.ROUTE_NOT_CONFIGURED);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("https://dapi.kakao.com/v2/routing/" + routeType)
                .queryParam("start_x", request.startX())
                .queryParam("start_y", request.startY())
                .queryParam("end_x", request.endX())
                .queryParam("end_y", request.endY())
                .queryParam("input_coord", "WGS84")
                .queryParam("output_coord", "WGS84");
        if (routeMode != null && !routeMode.isBlank()) {
            builder.queryParam("route_mode", routeMode);
        }

        try {
            String json = RestClient.create().get().uri(builder.build().encode().toUriString())
                    .header("Authorization", "KakaoAK " + kakaoKey)
                    .header("Accept", "application/json")
                    .retrieve().body(String.class);
            return objectMapper.readTree(json);
        } catch (RestClientResponseException error) {
            log.warn("Kakao map {} route rejected request: status={}, body={}",
                    routeType, error.getStatusCode().value(), error.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ROUTE_API_FAILED);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            log.warn("Kakao map {} route request failed", routeType, error);
            throw new BusinessException(ErrorCode.ROUTE_API_FAILED);
        }
    }

    private void addPathPoints(
            List<TransitRouteResponse.RoutePoint> points,
            JsonNode pathPoints) {
        if (!pathPoints.isArray()) return;
        for (JsonNode point : pathPoints) {
            if (!point.isArray() || point.size() < 2) continue;
            addPoint(points, point.path(0).asDouble(Double.NaN),
                    point.path(1).asDouble(Double.NaN));
        }
    }

    private void addVertexPoints(
            List<TransitRouteResponse.RoutePoint> points,
            JsonNode vertexes) {
        if (!vertexes.isArray()) return;
        for (int index = 0; index + 1 < vertexes.size(); index += 2) {
            addPoint(points, vertexes.path(index).asDouble(Double.NaN),
                    vertexes.path(index + 1).asDouble(Double.NaN));
        }
    }

    private void addPoint(List<TransitRouteResponse.RoutePoint> points, double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) return;
        if (!points.isEmpty()) {
            TransitRouteResponse.RoutePoint previous = points.get(points.size() - 1);
            if (Double.compare(previous.longitude(), longitude) == 0
                    && Double.compare(previous.latitude(), latitude) == 0) return;
        }
        points.add(new TransitRouteResponse.RoutePoint(longitude, latitude));
    }

    public RouteOptimizationResponse optimize(Long userId, Long tripDayId) {
        return optimize(userId, tripDayId, OptimizationCriterion.TIME.name());
    }

    public RouteOptimizationResponse optimize(Long userId, Long tripDayId, String criterionValue) {
        OptimizationCriterion criterion = OptimizationCriterion.parse(criterionValue);
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_CONFIGURED);
        }
        List<ItineraryItemDTO> allItems = tripService.getItems(userId, tripDayId);
        List<ItineraryItemDTO> placeItems = allItems.stream()
                .filter(item -> item.getPlaceId() != null)
                .toList();
        if (placeItems.size() < 2) {
            PathMetrics empty = PathMetrics.empty();
            return response(allItems, empty, empty, false);
        }

        Map<Long, PlaceDTO> places = new HashMap<>();
        for (ItineraryItemDTO item : placeItems) {
            places.put(item.getItineraryItemId(), placeService.get(item.getPlaceId()));
        }

        PathMetrics originalMetrics = measurePath(placeItems, places, criterion);

        List<ItineraryItemDTO> remaining = new ArrayList<>(placeItems);
        List<ItineraryItemDTO> orderedPlaces = new ArrayList<>();
        ItineraryItemDTO current = remaining.remove(0);
        orderedPlaces.add(current);
        int totalDuration = 0;
        int totalDistance = 0;
        boolean distancePriorityApplied = false;

        while (!remaining.isEmpty()) {
            Leg best = null;
            for (ItineraryItemDTO candidate : remaining) {
                Leg measured = directions(places.get(current.getItineraryItemId()),
                        places.get(candidate.getItineraryItemId()), criterion);
                if (measured == null) continue;
                boolean distanceTieBreak = criterion == OptimizationCriterion.TIME
                        && best != null
                        && measured.durationSeconds() == best.durationSeconds()
                        && measured.distanceMeters() < best.distanceMeters();
                boolean timeTieBreak = criterion == OptimizationCriterion.DISTANCE
                        && best != null
                        && measured.distanceMeters() == best.distanceMeters()
                        && measured.durationSeconds() < best.durationSeconds();
                boolean betterPrimaryValue = best == null
                        || (criterion == OptimizationCriterion.TIME
                        ? measured.durationSeconds() < best.durationSeconds()
                        : measured.distanceMeters() < best.distanceMeters());
                if (betterPrimaryValue || distanceTieBreak || timeTieBreak) {
                    if (distanceTieBreak) distancePriorityApplied = true;
                    best = new Leg(candidate, measured.durationSeconds(), measured.distanceMeters());
                }
            }
            if (best == null) {
                throw new IllegalArgumentException("자동차 경로를 찾을 수 없는 장소가 일정에 포함되어 있습니다.");
            }
            orderedPlaces.add(best.item());
            remaining.remove(best.item());
            totalDuration += best.durationSeconds();
            totalDistance += best.distanceMeters();
            current = best.item();
        }

        List<ItineraryItemDTO> orderedAllItems = mergePlaceOrder(allItems, orderedPlaces);
        PathMetrics optimizedMetrics = measurePath(orderedPlaces, places, criterion);
        if (optimizedMetrics == null) {
            optimizedMetrics = new PathMetrics(totalDuration, totalDistance, List.of());
        }
        return response(orderedAllItems, optimizedMetrics, originalMetrics, distancePriorityApplied);
    }

    private List<ItineraryItemDTO> mergePlaceOrder(
            List<ItineraryItemDTO> allItems,
            List<ItineraryItemDTO> orderedPlaces) {
        List<ItineraryItemDTO> merged = new ArrayList<>(allItems.size());
        int placeIndex = 0;
        for (ItineraryItemDTO item : allItems) {
            if (item.getPlaceId() != null && placeIndex < orderedPlaces.size()) {
                merged.add(orderedPlaces.get(placeIndex++));
            } else {
                merged.add(item);
            }
        }
        return merged;
    }

    @Transactional
    public void reorder(Long userId, Long tripDayId, List<Long> itemIds) {
        tripService.reorderItems(userId, tripDayId, itemIds);
    }

    private Leg directions(PlaceDTO origin, PlaceDTO destination, OptimizationCriterion criterion) {
        if (origin == null || destination == null || origin.getLatitude() == null || origin.getLongitude() == null
                || destination.getLatitude() == null || destination.getLongitude() == null) {
            throw new IllegalArgumentException("모든 일정 장소에 좌표가 필요합니다.");
        }
        String kakaoKey = restApiKey == null ? "" : restApiKey.trim();
        if (kakaoKey.isBlank()) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_CONFIGURED);
        }
        String cacheKey = routeCacheKey(origin, destination, criterion);
        RouteLegResult cached = getCachedRoute(cacheKey);
        if (cached != null) {
            return new Leg(null, cached.durationSeconds(), cached.distanceMeters());
        }

        String url = UriComponentsBuilder
                .fromUriString("https://apis-navi.kakaomobility.com/v1/directions")
                .queryParam("origin", origin.getLongitude() + "," + origin.getLatitude())
                .queryParam("destination", destination.getLongitude() + "," + destination.getLatitude())
                .queryParam("priority", criterion.name())
                .queryParam("summary", "true")
                .build()
                .toUriString();
        try {
            String json = RestClient.create().get().uri(url)
                    .header("Authorization", "KakaoAK " + kakaoKey)
                    .header("Content-Type", "application/json")
                    .retrieve().body(String.class);
            JsonNode route = objectMapper.readTree(json).path("routes").path(0);
            if (route.path("result_code").asInt(-1) != 0) return null;
            JsonNode summary = route.path("summary");
            if (summary.isMissingNode() || summary.path("duration").isMissingNode()) return null;
            RouteLegResult result = new RouteLegResult(
                    summary.path("duration").asInt(),
                    summary.path("distance").asInt());
            putCachedRoute(cacheKey, result);
            return new Leg(null, result.durationSeconds(), result.distanceMeters());
        } catch (RestClientResponseException error) {
            log.warn("Kakao Mobility directions rejected request: status={}, body={}",
                    error.getStatusCode().value(), error.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ROUTE_API_FAILED);
        } catch (Exception error) {
            log.warn("Kakao Mobility directions request failed", error);
            throw new BusinessException(ErrorCode.ROUTE_API_FAILED);
        }
    }

    private RouteOptimizationResponse response(
            List<ItineraryItemDTO> items,
            PathMetrics optimized,
            PathMetrics original,
            boolean distancePriorityApplied) {
        boolean originalAvailable = original != null;
        int originalDuration = originalAvailable ? original.durationSeconds() : 0;
        int originalDistance = originalAvailable ? original.distanceMeters() : 0;
        int savedDuration = originalAvailable
                ? Math.max(0, originalDuration - optimized.durationSeconds())
                : 0;
        return new RouteOptimizationResponse(
                items.stream().map(ItineraryItemDTO::getItineraryItemId).toList(),
                items.stream().map(ItineraryItemDTO::getTitle).toList(),
                optimized.durationSeconds(),
                optimized.distanceMeters(),
                distancePriorityApplied,
                optimized.segments(),
                originalDuration,
                originalDistance,
                optimized.durationSeconds(),
                optimized.distanceMeters(),
                savedDuration,
                originalAvailable
        );
    }

    private PathMetrics measurePath(
            List<ItineraryItemDTO> items,
            Map<Long, PlaceDTO> places,
            OptimizationCriterion criterion) {
        if (items.size() < 2) return PathMetrics.empty();
        int totalDuration = 0;
        int totalDistance = 0;
        List<RouteSegment> segments = new ArrayList<>();
        for (int index = 0; index < items.size() - 1; index++) {
            ItineraryItemDTO from = items.get(index);
            ItineraryItemDTO to = items.get(index + 1);
            Leg leg = directions(
                    places.get(from.getItineraryItemId()),
                    places.get(to.getItineraryItemId()),
                    criterion);
            if (leg == null) return null;
            totalDuration += leg.durationSeconds();
            totalDistance += leg.distanceMeters();
            segments.add(new RouteSegment(
                    from.getItineraryItemId(), from.getTitle(),
                    to.getItineraryItemId(), to.getTitle(),
                    leg.durationSeconds(), leg.distanceMeters()));
        }
        return new PathMetrics(totalDuration, totalDistance, segments);
    }

    private String routeCacheKey(
            PlaceDTO origin,
            PlaceDTO destination,
            OptimizationCriterion criterion) {
        return String.join(":",
                criterion.name(),
                coordinate(origin.getLatitude()), coordinate(origin.getLongitude()),
                coordinate(destination.getLatitude()), coordinate(destination.getLongitude()));
    }

    private String coordinate(java.math.BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private RouteLegResult getCachedRoute(String key) {
        Cache cache = cacheManager.getCache("routeDirections");
        if (cache == null) return null;
        try {
            Cache.ValueWrapper wrapper = cache.get(key);
            Object value = wrapper == null ? null : wrapper.get();
            if (value instanceof RouteLegResult result) return result;
            if (value instanceof String text) {
                String[] parts = text.split(":", -1);
                if (parts.length == 2) {
                    return new RouteLegResult(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }
            return null;
        } catch (RuntimeException error) {
            log.warn("경로 캐시 조회 실패로 Kakao API를 호출합니다. key={}", key, error);
            return null;
        }
    }

    private void putCachedRoute(String key, RouteLegResult result) {
        Cache cache = cacheManager.getCache("routeDirections");
        if (cache == null) return;
        try {
            cache.put(key, result.durationSeconds() + ":" + result.distanceMeters());
        } catch (RuntimeException error) {
            log.warn("경로 캐시 저장 실패. key={}", key, error);
        }
    }

    private record Leg(ItineraryItemDTO item, int durationSeconds, int distanceMeters) {}

    private record RouteLegResult(int durationSeconds, int distanceMeters) {}

    private enum OptimizationCriterion {
        TIME,
        DISTANCE;

        private static OptimizationCriterion parse(String value) {
            if (value == null || value.isBlank()) return TIME;
            try {
                return OptimizationCriterion.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("지원하지 않는 동선 최적화 기준입니다.");
            }
        }
    }

    private record PathMetrics(
            int durationSeconds,
            int distanceMeters,
            List<RouteSegment> segments) {
        private static PathMetrics empty() {
            return new PathMetrics(0, 0, List.of());
        }
    }
}
