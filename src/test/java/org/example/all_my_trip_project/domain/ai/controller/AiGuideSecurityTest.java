package org.example.all_my_trip_project.domain.ai.controller;

import org.example.all_my_trip_project.domain.ai.service.AiGuideRequestGuard;
import org.example.all_my_trip_project.domain.ai.service.AiGuideService;
import org.example.all_my_trip_project.global.config.ApiSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiGuideController.class)
@Import(ApiSecurityConfig.class)
class AiGuideSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGuideService aiGuideService;

    @MockitoBean
    private AiGuideRequestGuard requestGuard;

    @Test
    void rejectsUnauthenticatedAiGuideGenerationRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ai-guides/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"여행 추천\"}"))
                .andExpect(status().isForbidden());
    }
}
