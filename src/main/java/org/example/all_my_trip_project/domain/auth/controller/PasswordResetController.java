package org.example.all_my_trip_project.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.auth.dto.PasswordResetConfirmRequest;
import org.example.all_my_trip_project.domain.auth.dto.PasswordResetRequest;
import org.example.all_my_trip_project.domain.auth.service.PasswordResetService;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * 재설정 링크 요청.
     *
     * <p>가입한 적 없는 이메일이어도 같은 답을 준다. 여기서 답이 갈리면 이메일을 넣어 보며
     * 누가 가입했는지 알아낼 수 있다. 링크는 응답이 아니라 메일로만 나간다.
     */
    @PostMapping
    public ApiResponse<Void> request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ApiResponse.success(
                "입력하신 이메일로 재설정 링크를 보냈습니다. 메일함을 확인해 주세요.", null);
    }

    /** 재설정 화면을 열기 전에 링크가 아직 쓸 수 있는지 본다. */
    @GetMapping
    public ApiResponse<Void> verify(@RequestParam String token) {
        passwordResetService.verifyToken(token);
        return ApiResponse.success("링크를 확인했습니다.", null);
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ApiResponse.success("비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.", null);
    }
}
