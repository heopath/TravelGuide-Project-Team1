package org.example.all_my_trip_project.domain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.place.service.PlaceService;
import org.example.all_my_trip_project.domain.route.dto.RouteOptimizationResponse;
import org.example.all_my_trip_project.domain.route.dto.RouteOptimizationResponse.RouteSegment;
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

    @Value("${kakao.mobility.rest-api-key:}")
    private String restApiKey;

    @Transactional
    public RouteOptimizationResponse optimize(Long userId, Long tripDayId) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_CONFIGURED);
        }
        List<ItineraryItemDTO> items = tripService.getItems(userId, tripDayId).stream()
                .filter(item -> item.getPlaceId() != null)
                .toList();
        if (items.size() < 2) {
            PathMetrics empty = PathMetrics.empty();
            return response(items, empty, empty, false);
        }

        Map<Long, PlaceDTO> places = new HashMap<>();
        for (ItineraryItemDTO item : items) {
            places.put(item.getItineraryItemId(), placeService.get(item.getPlaceId()));
        }

        PathMetrics originalMetrics = measurePath(items, places);

        List<ItineraryItemDTO> remaining = new ArrayList<>(items);
        List<ItineraryItemDTO> ordered = new ArrayList<>();
        ItineraryItemDTO current = remaining.remove(0);
        ordered.add(current);
        int totalDuration = 0;
        int totalDistance = 0;
        boolean distancePriorityApplied = false;

        while (!remaining.isEmpty()) {
            Leg best = null;
            for (ItineraryItemDTO candidate : remaining) {
                Leg measured = directions(places.get(current.getItineraryItemId()),
                        places.get(candidate.getItineraryItemId()));
                if (measured == null) continue;
                boolean distanceTieBreak = best != null
                        && measured.durationSeconds() == best.durationSeconds()
                        && measured.distanceMeters() < best.distanceMeters();
                if (best == null
                        || measured.durationSeconds() < best.durationSeconds()
                        || distanceTieBreak) {
                    if (distanceTieBreak) distancePriorityApplied = true;
                    best = new Leg(candidate, measured.durationSeconds(), measured.distanceMeters());
                }
            }
            if (best == null) {
                throw new IllegalArgumentException("자동차 경로를 찾을 수 없는 장소가 일정에 포함되어 있습니다.");
            }
            ordered.add(best.item());
            remaining.remove(best.item());
            totalDuration += best.durationSeconds();
            totalDistance += best.distanceMeters();
            current = best.item();
        }

        tripService.reorderItems(userId, tripDayId,
                ordered.stream().map(ItineraryItemDTO::getItineraryItemId).toList());
        PathMetrics optimizedMetrics = measurePath(ordered, places);
        if (optimizedMetrics == null) {
            optimizedMetrics = new PathMetrics(totalDuration, totalDistance, List.of());
        }
        return response(ordered, optimizedMetrics, originalMetrics, distancePriorityApplied);
    }

    @Transactional
    public void reorder(Long userId, Long tripDayId, List<Long> itemIds) {
        tripService.reorderItems(userId, tripDayId, itemIds);
    }

    private Leg directions(PlaceDTO origin, PlaceDTO destination) {
        if (origin == null || destination == null || origin.getLatitude() == null || origin.getLongitude() == null
                || destination.getLatitude() == null || destination.getLongitude() == null) {
            throw new IllegalArgumentException("모든 일정 장소에 좌표가 필요합니다.");
        }
        String cacheKey = routeCacheKey(origin, destination);
        RouteLegResult cached = getCachedRoute(cacheKey);
        if (cached != null) {
            return new Leg(null, cached.durationSeconds(), cached.distanceMeters());
        }

        String url = UriComponentsBuilder
                .fromUriString("https://apis-navi.kakaomobility.com/v1/directions")
                .queryParam("origin", origin.getLongitude() + "," + origin.getLatitude())
                .queryParam("destination", destination.getLongitude() + "," + destination.getLatitude())
                .queryParam("priority", "TIME")
                .queryParam("summary", "true")
                .build()
                .toUriString();
        try {
            String json = RestClient.create().get().uri(url)
                    .header("Authorization", "KakaoAK " + restApiKey)
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

    private PathMetrics measurePath(List<ItineraryItemDTO> items, Map<Long, PlaceDTO> places) {
        if (items.size() < 2) return PathMetrics.empty();
        int totalDuration = 0;
        int totalDistance = 0;
        List<RouteSegment> segments = new ArrayList<>();
        for (int index = 0; index < items.size() - 1; index++) {
            ItineraryItemDTO from = items.get(index);
            ItineraryItemDTO to = items.get(index + 1);
            Leg leg = directions(places.get(from.getItineraryItemId()), places.get(to.getItineraryItemId()));
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

    private String routeCacheKey(PlaceDTO origin, PlaceDTO destination) {
        return String.join(":",
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

    private record PathMetrics(
            int durationSeconds,
            int distanceMeters,
            List<RouteSegment> segments) {
        private static PathMetrics empty() {
            return new PathMetrics(0, 0, List.of());
        }
    }
}
