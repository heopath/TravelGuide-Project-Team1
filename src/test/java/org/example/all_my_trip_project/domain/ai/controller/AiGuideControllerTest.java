package org.example.all_my_trip_project.domain.ai.controller;

import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideItemResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.service.AiGuideRequestGuard;
import org.example.all_my_trip_project.domain.ai.service.AiGuideService;
import org.example.all_my_trip_project.domain.ai.service.AiModelException;
import org.example.all_my_trip_project.global.exception.ApiExceptionHandler;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiGuideControllerTest {
    @Mock private AiGuideService aiGuideService;
    @Mock private AiGuideRequestGuard requestGuard;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "ai-test@example.com", "USER"), null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new AiGuideController(aiGuideService, requestGuard))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateReturnsGuideForPositiveTripId() throws Exception {
        AiGuideResponse response = new AiGuideResponse("Guide", List.of(new AiGuideDayResponse(1, "DAY 1",
                List.of(new AiGuideItemResponse("18:00", "Dinner", "Nearby")))), List.of(), List.of());
        when(aiGuideService.generate(any(), eq(false), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Recommend dinner\",\"tripId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("Guide"));

        verify(aiGuideService).generate(any(), eq(false), isNull());
    }

    @Test
    void generateRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"tripId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void generateRejectsQuestionLongerThanFiveHundredCharacters() throws Exception {
        String tooLongQuestion = "a".repeat(501);
        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"%s\",\"tripId\":1}".formatted(tooLongQuestion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void generateRejectsNonPositiveTripId() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Recommend dinner\",\"tripId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("tripId"));
    }

    @Test
    void generateRejectsNonPositiveSelectedDayNumber() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Recommend dinner\",\"tripId\":1,\"selectedDayNumber\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("selectedDayNumber"));
    }

    @Test
    void generateRejectsMissingTripId() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Recommend dinner\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("tripId"));
    }

    @Test
    void generateReturnsTripNotFoundForMissingOrUnownedTrip() throws Exception {
        doThrow(new BusinessException(ErrorCode.TRIP_NOT_FOUND)).when(aiGuideService).generate(any(), eq(false), isNull());
        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Recommend dinner\",\"tripId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void generateReturnsInternalErrorForMockFailure() throws Exception {
        doThrow(new IllegalStateException("AI mock server error")).when(aiGuideService).generate(any(), eq(true), isNull());
        mockMvc.perform(post("/api/v1/ai-guides/generate").header("X-AI-Mock-Mode", "server-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Failure test\",\"tripId\":1}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void generateReturnsSafeGatewayErrorWhenAiModelFails() throws Exception {
        doThrow(new AiModelException("Gemini request timed out")).when(aiGuideService).generate(any(), eq(false), isNull());
        mockMvc.perform(post("/api/v1/ai-guides/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Timeout test\",\"tripId\":1}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_GENERATION_FAILED"))
                .andExpect(jsonPath("$.message").value(not(containsString("Gemini request timed out"))));
    }

    @Test
    void resetConversationResetsOnlyTheAuthenticatedUsersRequestedTrip() throws Exception {
        mockMvc.perform(delete("/api/v1/ai-guides/conversation").param("tripId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(aiGuideService).resetConversation(isNull(), eq(1L));
    }
}
