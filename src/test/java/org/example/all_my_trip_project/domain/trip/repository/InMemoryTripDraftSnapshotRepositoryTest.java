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
    void storesAndReturnsDefensiveCopiesForOwner() {
        List<String> purposes = new ArrayList<>(List.of("FOOD"));
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("purposes", purposes);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("basic", Map.of("destination", "서울"));
        draft.put("style", style);

        var created = repository.create(1L, draft);
        purposes.add("PHOTO");
        ((Map<String, Object>) created.draft().get("style")).put("pace", "PACKED");

        var restored = repository.findById(created.draftId(), 1L).orElseThrow();
        Map<?, ?> restoredStyle = (Map<?, ?>) restored.draft().get("style");

        assertThat(restoredStyle.get("purposes")).isEqualTo(List.of("FOOD"));
        assertThat(restoredStyle.containsKey("pace")).isFalse();
        assertThat(repository.findById(created.draftId(), 2L)).isEmpty();
    }

    @Test
    void returnsEmptyWhenUpdatingAnUnknownOrForeignDraft() {
        assertThat(repository.update("missing", 1L, Map.of("basic", Map.of()))).isEmpty();
        var created = repository.create(1L, Map.of("basic", Map.of("destination", "서울")));
        assertThat(repository.update(created.draftId(), 2L,
                Map.of("basic", Map.of("destination", "부산")))).isEmpty();
    }
}
