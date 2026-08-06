package org.example.all_my_trip_project.global.config;

import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.example.all_my_trip_project.presentation.page.AdminPageController;
import org.example.all_my_trip_project.presentation.page.MemberPageController;
import org.example.all_my_trip_project.presentation.page.TripPageController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 페이지 체인은 @Profile("!ui")이므로 test 프로필로 활성화한다.
// ui 프로필에는 로그인 API가 없어 전부 permitAll인 별도 체인이 뜬다.
@WebMvcTest({TripPageController.class, MemberPageController.class, AdminPageController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class PageAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/trips/new/basic", "/trips/new/style", "/trips/1/schedule", "/mypage"})
    void redirectsAnonymousAccessToLogin(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?redirect=" + urlEncoded(path)));
    }

    // 쿼리 문자열까지 보존해야 로그인 후 같은 화면으로 정확히 돌아간다.
    // param()은 getQueryString()을 채우지 않으므로 URL에 직접 붙인다.
    @Test
    void keepsQueryStringInRedirectTarget() throws Exception {
        mockMvc.perform(get("/trips/1/schedule?day=2"))
                .andExpect(redirectedUrl("/auth/login?redirect="
                        + urlEncoded("/trips/1/schedule") + "%3Fday%3D2"));
    }

    @Test
    void allowsAuthenticatedAccessToTripPages() throws Exception {
        mockMvc.perform(get("/trips/new/basic").with(authentication(user("USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAdminPageForNormalUser() throws Exception {
        mockMvc.perform(get("/admin").with(authentication(user("USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminPageForAdminRole() throws Exception {
        mockMvc.perform(get("/admin").with(authentication(user("ADMIN"))))
                .andExpect(status().isOk());
    }

    // 정적 리소스가 체인에 걸리면 비로그인 상태에서 로그인 화면으로 리다이렉트되어 화면이 깨진다.
    @Test
    void doesNotBlockStaticResources() throws Exception {
        mockMvc.perform(get("/css/pages/trips/schedule.css"))
                .andExpect(status().isOk());
    }

    private static String urlEncoded(String path) {
        return path.replace("/", "%2F");
    }

    private UsernamePasswordAuthenticationToken user(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "page-test@example.com", role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
