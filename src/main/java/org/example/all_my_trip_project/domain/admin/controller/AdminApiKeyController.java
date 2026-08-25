package org.example.all_my_trip_project.domain.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyDTO;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyListDTO;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyTestRequest;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyTestResultDTO;
import org.example.all_my_trip_project.domain.admin.dto.ApiKeyUpdateRequest;
import org.example.all_my_trip_project.domain.admin.service.AdminApiKeyService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 API 키 관리.
 *
 * <p>{@code /api/v1/admin/**}은 {@code ApiSecurityConfig}에서 {@code hasRole("ADMIN")}으로
 * 이미 막혀 있다. 여기에 별도 권한 검사를 두지 않는 것은 그 설정을 신뢰하기 때문이며,
 * 두 곳에 규칙이 갈리면 나중에 한쪽만 고쳐지는 쪽이 더 위험하다.
 *
 * <p><b>응답에는 키 전체 값이 없다.</b> 조회는 마스킹된 값만 돌려준다. 전체 값을 돌려주는
 * 엔드포인트는 만들지 않았다. 관리자 계정 하나가 뚫렸을 때 키까지 함께 새 나가는 경로를
 * 두지 않기 위해서다. 키를 잃어버렸다면 발급처에서 새로 만들어 넣는 것이 맞다.
 */
@RestController
@Profile("!ui")
@RequestMapping("/api/v1/admin/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private final AdminApiKeyService adminApiKeyService;

    @GetMapping
    public ApiResponse<ApiKeyListDTO> list() {
        return ApiResponse.success(
                new ApiKeyListDTO(adminApiKeyService.isEncryptionReady(), adminApiKeyService.list()));
    }

    /**
     * 연결 테스트. 저장하지 않는다.
     *
     * <p>조회지만 {@code POST}인 이유는 본문에 키를 담기 때문이다. {@code GET}으로 만들면 키가
     * 주소에 들어가고, 주소는 브라우저 기록과 서버 접근 로그에 그대로 남는다.
     */
    @PostMapping("/{name}/test")
    public ApiResponse<ApiKeyTestResultDTO> test(@PathVariable String name,
                                                 @RequestBody(required = false) ApiKeyTestRequest request) {
        String candidate = request == null ? null : request.apiKey();
        return ApiResponse.success(adminApiKeyService.test(name, candidate));
    }

    @PutMapping("/{name}")
    public ApiResponse<ApiKeyDTO> update(@AuthenticationPrincipal AuthenticatedUser admin,
                                         @PathVariable String name,
                                         @Valid @RequestBody ApiKeyUpdateRequest request) {
        ApiKeyDTO result = adminApiKeyService.update(name, request.apiKey(), admin.userId());
        return ApiResponse.success("API 키를 변경했습니다.", result);
    }

    @DeleteMapping("/{name}")
    public ApiResponse<ApiKeyDTO> reset(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @PathVariable String name) {
        ApiKeyDTO result = adminApiKeyService.reset(name, admin.userId());
        return ApiResponse.success("저장한 키를 지우고 서버 환경변수 값으로 되돌렸습니다.", result);
    }
}
