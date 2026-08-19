package org.example.all_my_trip_project.presentation.advice;

import org.example.all_my_trip_project.domain.admin.service.ServiceVersionService;
import org.example.all_my_trip_project.presentation.page.HomePageController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomePageController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FooterVersionRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceVersionService serviceVersionService;

    @Test
    void rendersStoredVersionInSharedFooter() throws Exception {
        when(serviceVersionService.displayVersion()).thenReturn("v1.2.3");

        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<span class=\"footer-version\">v1.2.3</span>")));
    }
}
