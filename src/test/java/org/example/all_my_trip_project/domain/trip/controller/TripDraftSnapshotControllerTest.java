package org.example.all_my_trip_project.domain.trip.controller;

import org.example.all_my_trip_project.domain.trip.service.TripDraftSnapshotService;
import org.example.all_my_trip_project.domain.trip.repository.InMemoryTripDraftSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TripDraftSnapshotControllerTest {

    private final TripDraftSnapshotService service =
            new TripDraftSnapshotService(new InMemoryTripDraftSnapshotRepository());
    private final TripDraftSnapshotController controller = new TripDraftSnapshotController(service);
    private final MockMvc mockMvc = standaloneSetup(controller).build();

    @Test
    void acceptsAJsonDraftThroughTheHttpMessageConverter() throws Exception {
        mockMvc.perform(post("/api/trip-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "basic": {"tripName": "HTTP 여행"},
                                  "style": {"purposes": ["FOOD"]}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.draft.basic.tripName").value("HTTP 여행"));
    }

    @Test
    void createsReadsAndUpdatesACompleteDraft() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("basic", Map.of("tripName", "여름 여행"));
        initial.put("style", Map.of("purposes", List.of("FOOD")));

        var created = controller.create(initial);
        var createdData = created.getBody().data();

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdData.draftId()).isNotBlank();
        assertThat(controller.get(createdData.draftId()).data().draft())
                .isEqualTo(initial);

        Map<String, Object> updated = new LinkedHashMap<>(initial);
        updated.put("recommendation", Map.of("destinationSlug", "yeosu"));

        var updatedData = controller.update(createdData.draftId(), updated).data();
        assertThat(((Map<?, ?>) updatedData.draft().get("recommendation")).get("destinationSlug"))
                .isEqualTo("yeosu");
    }

    @Test
    void rejectsAnIncompleteDraft() {
        assertThatThrownBy(() -> controller.create(Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void returnsNotFoundForAnUnknownDraft() {
        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(TripDraftSnapshotService.DraftNotFoundException.class);
    }
}
