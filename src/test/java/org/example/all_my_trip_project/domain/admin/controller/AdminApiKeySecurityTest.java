package org.example.all_my_trip_project.domain.admin.controller;

import org.example.all_my_trip_project.domain.admin.dto.ApiKeyDTO;
import org.example.all_my_trip_project.domain.admin.service.AdminApiKeyService;
import org.example.all_my_trip_project.global.config.ApiSecurityConfig;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 키 관리 API의 인증·권한·CSRF 동작.
 *
 * <p>여기서 지키려는 것은 두 가지다. 하나는 <b>관리자가 아닌 요청이 키를 바꾸지 못하는 것</b>,
 * 다른 하나는 <b>응답에 키 전체 값이 실리지 않는 것</b>이다. 후자는 시큐리티 설정이 아니라
 * 컨트롤러 계약이라 설정을 고쳐도 지켜지지 않는다. 그래서 따로 검증한다.
 */
@WebMvcTest(AdminApiKeyController.class)
@Import(ApiSecurityConfig.class)
@ActiveProfiles("test")
class AdminApiKeySecurityTest {

    private static final String ENDPOINT = "/api/v1/admin/api-keys";
    private static final String KEY_ENDPOINT = ENDPOINT + "/OPENAI";

    /** DTO에 필수값이 늘어나도 한 곳만 고치면 되도록 본문을 상수로 둔다. */
    private static final String KEY_JSON = """
            {"apiKey":"sk-test-1234567890abcdef"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminApiKeyService adminApiKeyService;

    @Test
    void rejectsAnonymousRead() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(adminApiKeyService, never()).list();
    }

    /** CSRF 실패는 로그인 여부와 무관하게 403이다. 익명 요청이어도 401로 바뀌지 않는다. */
    @Test
    void rejectsAnonymousUpdateWithoutCsrfToken() throws Exception {
        mockMvc.perform(put(KEY_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KEY_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnonymousUpdateWithCsrfToken() throws Exception {
        mockMvc.perform(put(KEY_ENDPOINT)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KEY_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNormalUser() throws Exception {
        mockMvc.perform(put(KEY_ENDPOINT)
                        .with(authentication(user("USER")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KEY_JSON))
                .andExpect(status().isForbidden());

        verify(adminApiKeyService, never()).update(eq("OPENAI"), eq("sk-test-1234567890abcdef"), eq(1L));
    }

    @Test
    void rejectsAdminWithoutCsrfToken() throws Exception {
        mockMvc.perform(put(KEY_ENDPOINT)
                        .with(authentication(user("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KEY_JSON))
                .andExpect(status().isForbidden());
    }

    /** 연결 테스트도 POST라 CSRF 대상이다. 조회처럼 보인다고 열어두면 안 된다. */
    @Test
    void rejectsConnectionTestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post(KEY_ENDPOINT + "/test")
                        .with(authentication(user("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KEY_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsResetWithoutCsrfToken() throws Exception {
        mockMvc.perform(delete(KEY_ENDPOINT)
                        .with(authentication(user("ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminAndPassesCurrentUserId() throws Exception {
        when(adminApiKeyService.update(eq("OPENAI"), eq("sk-test-1234567890abcdef"), eq(1L)))
                .thenReturn(storedKey());

        mockMvc.perform(put(KEY_ENDPOINT)
                        .with(authentication(user("ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KEY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedValue").value("sk-••••••••cdef"));

        verify(adminApiKeyService).update(eq("OPENAI"), eq("sk-test-1234567890abcdef"), eq(1L));
    }

    @Test
    void rejectsBlankApiKeyBeforeServiceCall() throws Exception {
        mockMvc.perform(put(KEY_ENDPOINT)
                        .with(authentication(user("ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(adminApiKeyService, never()).update(eq("OPENAI"), eq("  "), eq(1L));
    }

    /**
     * 조회 응답에 키 전체 값이 들어갈 자리가 없어야 한다.
     *
     * <p>{@code apiKey} 필드가 존재하지 않는지까지 확인하는 이유는, DTO에 필드가 하나 늘어나는
     * 것만으로 마스킹이 무력화되기 때문이다. 값이 비어 있는지가 아니라 <b>필드가 없는지</b>를 본다.
     */
    @Test
    void neverExposesFullKeyToAdmin() throws Exception {
        when(adminApiKeyService.isEncryptionReady()).thenReturn(true);
        when(adminApiKeyService.list()).thenReturn(List.of(storedKey()));

        mockMvc.perform(get(ENDPOINT)
                        .with(authentication(user("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.encryptionReady").value(true))
                .andExpect(jsonPath("$.data.keys[0].name").value("OPENAI"))
                .andExpect(jsonPath("$.data.keys[0].maskedValue").value("sk-••••••••cdef"))
                .andExpect(jsonPath("$.data.keys[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.keys[0].encryptedValue").doesNotExist());
    }

    private ApiKeyDTO storedKey() {
        return new ApiKeyDTO("OPENAI", "OpenAI", "AI 여행 추천에 사용합니다.",
                "sk-••••••••cdef", ApiKeyDTO.SOURCE_STORED, null, 1L);
    }

    private UsernamePasswordAuthenticationToken user(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(1L, "apikey-test@example.com", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
