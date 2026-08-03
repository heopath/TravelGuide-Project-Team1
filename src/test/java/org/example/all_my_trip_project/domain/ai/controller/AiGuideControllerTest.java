package org.example.all_my_trip_project.domain.ai.controller;

import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideItemResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.ai.service.AiGuideService;
import org.example.all_my_trip_project.global.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiGuideControllerTest {

    @Mock
    private AiGuideService aiGuideService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiGuideController(aiGuideService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void generateReturnsMockGuideResponse() throws Exception {
        AiGuideResponse response = new AiGuideResponse(
                "DAY 1 일정을 추천합니다.",
                List.of(new AiGuideDayResponse(
                        1,
                        "DAY 1 · 광안리 미식 산책",
                        List.of(new AiGuideItemResponse("18:00", "민락회센터", "저녁 식사에 적합"))
                )),
                List.of(),
                List.of("현재 일정")
        );
        org.mockito.Mockito.when(aiGuideService.generate(any(), eq(false))).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"근처 저녁 맛집을 추천해줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.answer").value("DAY 1 일정을 추천합니다."))
                .andExpect(jsonPath("$.data.days[0].day").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].name").value("민락회센터"));

        verify(aiGuideService).generate(any(), eq(false));
    }

    @Test
    void generateRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void generateRejectsQuestionLongerThanFiveHundredCharacters() throws Exception {
        String tooLongQuestion = "a".repeat(501);

        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"%s\"}".formatted(tooLongQuestion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void generateReturnsServerErrorForMockFailureMode() throws Exception {
        doThrow(new IllegalStateException("AI mock server error"))
                .when(aiGuideService).generate(any(), eq(true));

        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .header("X-AI-Mock-Mode", "server-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"서버 오류 테스트\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버에서 오류가 발생했습니다."))
                .andExpect(jsonPath("$.message").value(not(containsString("AI mock server error"))));

        verify(aiGuideService).generate(any(), eq(true));
    }
}
