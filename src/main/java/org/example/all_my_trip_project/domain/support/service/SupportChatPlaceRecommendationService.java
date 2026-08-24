package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.rag.service.PlaceRagService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class SupportChatPlaceRecommendationService {
    private final ObjectProvider<PlaceRagService> placeRagServiceProvider;
    private final PlaceDAO placeDAO;

    /** RAG가 켜진 환경에서만 실제 장소 후보를 제공한다. 장애나 미설정은 빈 카드로 안전하게 끝낸다. */
    public List<SupportChatPlaceCandidate> candidates(String question) {
        PlaceRagService ragService = placeRagServiceProvider.getIfAvailable();
        if (ragService == null) return List.of();
        return ragService.search(question).stream()
                .filter(result -> result.placeId() != null && result.placeName() != null)
                .map(result -> new SupportChatPlaceCandidate(
                        result.placeId(), result.placeName(), result.category(), result.address(), result.content()))
                .toList();
    }

    /** AI가 후보에 없던 번호를 써도 버리고, DB에 현재 존재하는 장소만 화면 데이터로 만든다. */
    public List<Map<String, Object>> cards(List<SupportChatPlaceSelection> selections,
                                           List<SupportChatPlaceCandidate> candidates) {
        if (selections == null || selections.isEmpty()) return List.of();
        Set<Long> allowedIds = candidates.stream().map(SupportChatPlaceCandidate::placeId).collect(Collectors.toSet());
        return selections.stream()
                .filter(selection -> selection.placeId() != null && allowedIds.contains(selection.placeId()))
                .distinct().limit(3)
                .map(selection -> placeDAO.findById(selection.placeId())
                        .filter(place -> Boolean.TRUE.equals(place.getActive()))
                        .map(place -> toCard(place, selection.reason())).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Map<String, Object> toCard(PlaceDTO place, String reason) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("placeId", place.getPlaceId());
        card.put("name", text(place.getName(), 150));
        card.put("category", text(place.getCategory(), 30));
        card.put("address", text(place.getAddress(), 255));
        card.put("description", text(place.getDescription(), 300));
        card.put("imageUrl", text(place.getPrimaryImageUrl(), 1000));
        card.put("rating", place.getAverageRating());
        card.put("reason", text(reason, 160));
        return card;
    }

    private String text(String value, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }
}
