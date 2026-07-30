package org.example.all_my_trip_project.domain.trip.repository;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTripDraftSnapshotRepositoryTest {

    private final InMemoryTripDraftSnapshotRepository repository =
            new InMemoryTripDraftSnapshotRepository();

    @Test
    @SuppressWarnings("unchecked")
    void storesAndReturnsDefensiveCopies() {
        List<String> purposes = new ArrayList<>(List.of("FOOD"));
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("purposes", purposes);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("basic", Map.of("tripName", "복원 여행"));
        draft.put("style", style);

        var created = repository.create(draft);
        purposes.add("PHOTO");
        ((Map<String, Object>) created.draft().get("style"))
                .put("pace", "PACKED");

        var restored = repository.findById(created.draftId()).orElseThrow();
        Map<?, ?> restoredStyle = (Map<?, ?>) restored.draft().get("style");

        assertThat(restoredStyle.get("purposes")).isEqualTo(List.of("FOOD"));
        assertThat(restoredStyle.containsKey("pace")).isFalse();
    }

    @Test
    void returnsEmptyWhenUpdatingAnUnknownDraft() {
        assertThat(repository.update(
                "missing",
                Map.of("basic", Map.of(), "style", Map.of())
        )).isEmpty();
    }
}
