package org.example.all_my_trip_project.domain.trip.controller;

import org.example.all_my_trip_project.domain.trip.repository.InMemoryTripDraftSnapshotRepository;
import org.example.all_my_trip_project.domain.trip.service.TripDraftSnapshotService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.SessionUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

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
    private final TripDraftSnapshotController controller =
            new TripDraftSnapshotController(service, new SessionUserResolver());
    private final MockMvc mockMvc = standaloneSetup(controller).build();

    @Test
    void savesBasicOnlyBeforeMovingToStyle() throws Exception {
        mockMvc.perform(post("/api/v1/trip-drafts")
                        .session(session(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basic\":{\"destination\":\"서울\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.draft.basic.destination").value("서울"));
    }

    @Test
    void updatesDraftAfterStyleSelection() {
        Map<String, Object> basicOnly = Map.of("basic", Map.of("destination", "서울"));
        var created = controller.create(basicOnly, request(1L)).getBody().data();

        Map<String, Object> completed = new LinkedHashMap<>(basicOnly);
        completed.put("style", Map.of("purposes", List.of("FOOD"), "scheduleStyle", "BALANCED"));

        var updated = controller.update(created.draftId(), completed, request(1L)).data();
        assertThat(updated.draft()).isEqualTo(completed);
    }

    @Test
    void blocksAnotherUsersDraftAccess() {
        var created = controller.create(
                Map.of("basic", Map.of("destination", "서울")), request(1L)).getBody().data();

        assertThatThrownBy(() -> controller.get(created.draftId(), request(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_DRAFT_NOT_FOUND);

        assertThatThrownBy(() -> controller.update(
                created.draftId(), Map.of("basic", Map.of("destination", "부산")), request(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.TRIP_DRAFT_NOT_FOUND);
    }

    @Test
    void requiresLoginForDraftSave() {
        assertThatThrownBy(() -> controller.create(
                Map.of("basic", Map.of("destination", "서울")), new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void rejectsEmptyDraft() {
        assertThatThrownBy(() -> controller.create(Map.of(), request(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MockHttpServletRequest request(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session(userId));
        return request;
    }

    private MockHttpSession session(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);
        return session;
    }
}
